package com.codeit.mople.domain.directmessage.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.conversation.repository.ConversationRepository;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.directmessage.repository.DirectMessageRepository;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.config.SecurityConfig;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(SecurityConfig.class)
@Transactional
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class DirectMessageIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private ConversationRepository conversationRepository;

  @Autowired
  private DirectMessageRepository directMessageRepository;

  @Autowired
  private RedisTemplate<String, Object> redisTemplate;

  private User userA;
  private User userB;
  private CustomUserDetails userDetailsA;
  private CustomUserDetails userDetailsB;
  private Conversation testConversation;

  @BeforeEach
  void setUp() {
    userA = userRepository.save(User.createUser("testA@test.com", "12345678", "userA"));
    userB = userRepository.save(User.createUser("testB@test.com", "12345678", "userB"));

    userDetailsA = new CustomUserDetails(userA.getId(), Role.USER);
    userDetailsB = new CustomUserDetails(userB.getId(), Role.USER);

    testConversation = conversationRepository.save(Conversation.createConversation(userA, userB));
  }

  @AfterEach
  void tearDown() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
  }

  @Nested
  @DisplayName("GET /api/conversations/{conversationId}/direct-messages (DM 목록 조회)")
  class GetDirectMessages {

    @Test
    @DisplayName("성공: 커서 기반 페이징이 정상 동작하며, 최초 조회 시 Redis에 읽음 워터마크가 찍힌다.")
    void get_messages_pagination_and_watermark_success() throws Exception {
      // given
      for (int i = 1; i <= 25; i++) {
        DirectMessage msg = DirectMessage.createMessage(testConversation, userA, userB, "메시지 " + i);
        directMessageRepository.save(msg);
        Thread.sleep(1);
      }

      // when & then
      String responseJson = mockMvc.perform(
              get("/api/conversations/{conversationId}/direct-messages", testConversation.getId())
                  .with(user(userDetailsB))
                  .param("limit", "20")
                  .accept(MediaType.APPLICATION_JSON))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").isArray())
          .andExpect(jsonPath("$.data.length()").value(20))
          .andExpect(jsonPath("$.hasNext").value(true))
          .andExpect(jsonPath("$.data[0].content").value("메시지 25")) // 가장 최신 메시지가 먼저 와야 함
          .andReturn().getResponse().getContentAsString();

      // 채팅방 목록 조회를 하는 순간, 가장 최신 메시지의 시간으로 Redis 대기열에 읽음 워터마크가 등록되어야 함
      Boolean hasDirty = redisTemplate.hasKey("{dm:read:dirty}");
      assertThat(hasDirty).isTrue();

      String dirtyMember = testConversation.getId() + ":" + userB.getId();
      Boolean isMember = redisTemplate.opsForSet().isMember("{dm:read:dirty}", dirtyMember);
      assertThat(isMember).isTrue();

      // when: 다음 페이지 요청
      String cursorStr = JsonPath.read(responseJson, "$.nextCursor");
      String idAfterStr = JsonPath.read(responseJson, "$.nextIdAfter");

      mockMvc.perform(
              get("/api/conversations/{conversationId}/direct-messages", testConversation.getId())
                  .with(user(userDetailsB))
                  .param("cursor", cursorStr)
                  .param("idAfter", idAfterStr)
                  .param("limit", "20")
                  .accept(MediaType.APPLICATION_JSON))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.hasNext").value(false));
    }
  }

  @Nested
  @DisplayName("POST /api/conversations/{conversationId}/direct-messages/{directMessageId}/read (단건 읽음 처리)")
  class ReadDirectMessage {

    @Test
    @DisplayName("성공: 수신자가 메시지 읽음 API를 호출하면, 204를 반환하고 실제 Redis Cache와 대기열(Dirty Set)이 업데이트된다.")
    void read_message_integration_success() throws Exception {
      // given
      DirectMessage message = DirectMessage.createMessage(testConversation, userA, userB, "확인해줘");
      directMessageRepository.save(message);

      // when
      mockMvc.perform(
              post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read",
                  testConversation.getId(), message.getId())
                  .with(user(userDetailsB))
                  .with(csrf()))
          .andDo(print())
          .andExpect(status().isNoContent());

      // then
      String dirtyMember = testConversation.getId() + ":" + userB.getId();
      assertThat(redisTemplate.opsForSet().isMember("{dm:read:dirty}", dirtyMember)).isTrue();

      String valueKey = "dm:read:" + dirtyMember;
      Object cachedValue = redisTemplate.opsForValue().get(valueKey);
      assertThat(cachedValue).isNotNull();

      Instant parsedTime = Instant.parse(cachedValue.toString());
      assertThat(parsedTime).isEqualTo(message.getCreatedAt());
    }

    @Test
    @DisplayName("실패: 발신자 본인이 자기가 보낸 메시지를 읽음 처리하려 하면 204를 반환하지만, Redis는 갱신되지 않는다. (조기 종료)")
    void read_message_by_sender_ignored() throws Exception {
      // given
      DirectMessage message = DirectMessage.createMessage(testConversation, userA, userB,
          "내가 보낸 메시지");
      directMessageRepository.save(message);

      // when
      mockMvc.perform(
              post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read",
                  testConversation.getId(), message.getId())
                  .with(user(userDetailsA))
                  .with(csrf()))
          .andExpect(status().isNoContent());

      // then
      String dirtyMember = testConversation.getId() + ":" + userA.getId();
      assertThat(redisTemplate.opsForSet().isMember("{dm:read:dirty}", dirtyMember)).isFalse();
      assertThat(redisTemplate.hasKey("dm:read:" + dirtyMember)).isFalse();
    }
  }
}