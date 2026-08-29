package com.codeit.mople.domain.directmessage.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.directmessage.document.DirectMessageDocument;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.directmessage.repository.DirectMessageSearchRepository;
import com.codeit.mople.domain.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class DirectMessageSearchRepositoryIntegrationTest {

  @Autowired
  private DirectMessageSearchRepository directMessageSearchRepository;

  @Autowired
  private ElasticsearchOperations elasticsearchOperations;

  @AfterEach
  void tearDown() {
    directMessageSearchRepository.deleteAll();
    elasticsearchOperations.indexOps(DirectMessageDocument.class).refresh();
  }

  @Test
  @DisplayName("성공: 한글 단어가 정확히 검색된다")
  void searchExactMatch_NoSpace() {
    // given
    String roomId1 = saveMessageToElasticsearch("피자먹자");
    String roomId2 = saveMessageToElasticsearch("피자 먹자");

    // when
    List<DirectMessageDocument> results = directMessageSearchRepository.findByContentMatchesAndConversationIdIn(
        "피자", List.of(roomId1, roomId2), PageRequest.of(0, 30)
    );

    // then
    assertThat(results).hasSize(2);
  }

  @Test
  @DisplayName("성공: 띄어쓰기가 포함된 한글 단어가 공백까지 정확히 일치하여 검색된다")
  void searchExactMatch_WithSpace() {
    // given
    String roomId = saveMessageToElasticsearch("피자 먹자");

    // when
    List<DirectMessageDocument> results = directMessageSearchRepository.findByContentMatchesAndConversationIdIn(
        "자 먹", List.of(roomId), PageRequest.of(0, 30)
    );

    // then
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getContent()).isEqualTo("피자 먹자");
  }

  @Test
  @DisplayName("성공: 언더바(_)가 포함된 영문 아이디 패턴이 유실 없이 검색된다")
  void searchExactMatch_Underscore() {
    // given
    String roomId = saveMessageToElasticsearch("a_A");

    // when
    List<DirectMessageDocument> results = directMessageSearchRepository.findByContentMatchesAndConversationIdIn(
        "_A", List.of(roomId), PageRequest.of(0, 30)
    );

    // then
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getContent()).isEqualTo("a_A");
  }

  @Test
  @DisplayName("성공: 느낌표(!) 등 특수문자 연속 패턴이 보존되어 검색된다")
  void searchExactMatch_ExclamationMarks() {
    // given
    String roomId = saveMessageToElasticsearch("와!!!!!");

    // when
    List<DirectMessageDocument> results = directMessageSearchRepository.findByContentMatchesAndConversationIdIn(
        "!!", List.of(roomId), PageRequest.of(0, 30)
    );

    // then
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getContent()).isEqualTo("와!!!!!");
  }

  @Test
  @DisplayName("성공: 하이픈(-)이 포함된 전화번호 형식이 유실 없이 검색된다")
  void searchExactMatch_PhoneNumber() {
    // given
    String roomId = saveMessageToElasticsearch("010-1111-2222");

    // when
    List<DirectMessageDocument> results = directMessageSearchRepository.findByContentMatchesAndConversationIdIn(
        "-1", List.of(roomId), PageRequest.of(0, 30)
    );

    // then
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getContent()).isEqualTo("010-1111-2222");
  }

  @Test
  @DisplayName("성공: 골뱅이(@)와 마침표(.)가 포함된 이메일 형식이 유실 없이 검색된다")
  void searchExactMatch_Email() {
    // given
    String roomId = saveMessageToElasticsearch("a@test.com");

    // when
    List<DirectMessageDocument> dotResults = directMessageSearchRepository.findByContentMatchesAndConversationIdIn(
        ".c", List.of(roomId), PageRequest.of(0, 30)
    );
    List<DirectMessageDocument> atResults = directMessageSearchRepository.findByContentMatchesAndConversationIdIn(
        "@t", List.of(roomId), PageRequest.of(0, 30)
    );

    // then
    assertThat(dotResults).hasSize(1);
    assertThat(dotResults.get(0).getContent()).isEqualTo("a@test.com");

    assertThat(atResults).hasSize(1);
    assertThat(atResults.get(0).getContent()).isEqualTo("a@test.com");
  }

  @Test
  @DisplayName("실패(검증): 띄어쓰기가 일치하지 않으면 match_phrase 조건에 의해 검색되지 않아야 한다")
  void searchNotMatch_WhenSpaceDiffers() {
    // given
    String roomId = saveMessageToElasticsearch("피자먹자");

    // when
    List<DirectMessageDocument> results = directMessageSearchRepository.findByContentMatchesAndConversationIdIn(
        "피자 먹자", List.of(roomId), PageRequest.of(0, 30)
    );

    // then: match_phrase 쿼리가 띄어쓰기를 엄격히 구분하여 결과를 반환하지 않아야 함
    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("실패(검증): 원본 텍스트에 없는 더 긴 단어나 조사를 붙여 검색하면 매칭되지 않아야 한다")
  void searchNotMatch_WhenKeywordIsLonger() {
    // given
    String roomId = saveMessageToElasticsearch("피자먹자");

    // when
    List<DirectMessageDocument> results = directMessageSearchRepository.findByContentMatchesAndConversationIdIn(
        "피자먹자고?", List.of(roomId), PageRequest.of(0, 30)
    );

    // then
    assertThat(results).isEmpty();
  }

  // 테스트용 Document 생성 및 ES 즉시 반영 유틸 메서드 (대화방 ID 반환)
  private String saveMessageToElasticsearch(String content) {
    DirectMessage mockMessage = mock(DirectMessage.class);
    Conversation mockConversation = mock(Conversation.class);
    User mockUser = mock(User.class);

    // ID 모의 객체 주입 및 생성된 대화방 ID 추출
    UUID conversationId = UUID.randomUUID();
    given(mockConversation.getId()).willReturn(conversationId);
    given(mockUser.getId()).willReturn(UUID.randomUUID());

    // 메시지 모의 객체 세팅
    given(mockMessage.getId()).willReturn(UUID.randomUUID());
    given(mockMessage.getConversation()).willReturn(mockConversation);
    given(mockMessage.getSender()).willReturn(mockUser);
    given(mockMessage.getContent()).willReturn(content);
    given(mockMessage.getCreatedAt()).willReturn(Instant.now());

    // 저장
    DirectMessageDocument document = DirectMessageDocument.from(mockMessage);
    directMessageSearchRepository.save(document);

    // 엘라스틱서치는 비동기로 데이터를 인덱싱하므로, 저장 직후 즉시 검색되도록 강제 새로고침(Refresh)
    elasticsearchOperations.indexOps(DirectMessageDocument.class).refresh();

    return conversationId.toString();
  }
}