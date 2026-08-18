package com.codeit.mople.global.sse.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.config.SecurityConfig;
import com.codeit.mople.global.sse.model.SseEvent;
import com.codeit.mople.global.sse.repository.SseEmitterRepository;
import com.codeit.mople.global.sse.repository.SseEventRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(SecurityConfig.class)
@Transactional
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SseIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private SseEmitterRepository emitterRepository;

  @Autowired
  private SseEventRepository sseEventRepository;

  @Autowired
  private UserRepository userRepository;

  private User savedUser;
  private CustomUserDetails userDetails;

  @BeforeEach
  void setUp() {
    savedUser = userRepository.save(
        User.createUser("test@test.com", "12345678", "test")
    );
    userDetails = new CustomUserDetails(savedUser.getId(), Role.USER);
  }

  @Nested
  @DisplayName("SSE 연결")
  class Connect {

    @Test
    @DisplayName("SSE 연결 성공")
    void connect_success() throws Exception {
      // given

      // BeforeEach에서 savedUser, userDetails를 초기화

      // when & then
      mockMvc.perform(get("/api/sse")
              .with(user(userDetails))
              // 프론트코드에서 Accept="text/event-stream"으로 서버에게 응답을 요구하고 있기 때문에 contentType가 아닌 accept 사용
              .accept(MediaType.TEXT_EVENT_STREAM)
          )
          .andExpect(status().isOk());

      // sseEmitter는 단위 테스트에서 검증
      assertThat(emitterRepository.find(savedUser.getId())).isNotNull();
    }

    @Test
    @DisplayName("SSE 연결 성공 - lastEventId 존재 시 유실 SSE 재전송")
    void connect_success_resendEvents() throws Exception {
      // given

      // BeforeEach에서 savedUser, userDetails를 초기화

      UUID lastEventId = UUID.randomUUID();
      UUID newEventId = UUID.randomUUID();

      SseEvent lastEvent = new SseEvent(
          lastEventId, savedUser.getId(), "notifications", "data");

      SseEvent newEvent = new SseEvent(
          newEventId, savedUser.getId(), "notifications", "data2"
      );

      sseEventRepository.save(lastEvent);
      sseEventRepository.save(newEvent);

      // when & then
      mockMvc.perform(get("/api/sse")
              .param("lastEventId", lastEventId.toString())
              .accept(MediaType.TEXT_EVENT_STREAM)
              .with(user(userDetails))
          )
          .andExpect(status().isOk())
          .andExpect(content().string(
              containsString(newEventId.toString())
          ))
          .andExpect(content().string(
              containsString("notifications")
          ))
          .andExpect(content().string(
              containsString("data2")
          ))
          .andExpect(content().string(
              not(containsString(lastEventId.toString()))
          ));

      // then
      assertThat(emitterRepository.find(savedUser.getId())).isNotNull();
    }

    @Test
    @DisplayName("SSE 연결 실패 - 인증되지 않은 사용자(401 에러)")
    void connect_fail_unauthorized() throws Exception {
      // given

      // BeforeEach에서 userDetails 초기화

      // when & then
      mockMvc.perform(get("/api/sse")
              .accept(MediaType.TEXT_EVENT_STREAM)
          )
          .andExpect(status().isUnauthorized());
    }
  }

}
