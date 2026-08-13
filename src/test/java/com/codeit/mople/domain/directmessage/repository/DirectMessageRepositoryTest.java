package com.codeit.mople.domain.directmessage.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.directmessage.dto.request.DirectMessageCursorRequest;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.config.JpaAuditingConfig;
import com.codeit.mople.global.config.QueryDslConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
public class DirectMessageRepositoryTest {

  @Autowired
  private DirectMessageRepository directMessageRepository;

  @Autowired
  private TestEntityManager tem;

  private Conversation targetConversation;
  private Conversation otherConversation;

  @BeforeEach
  void setup() {
    User sender = tem.persist(User.createUser("sender@test.com", "12345678", "발신자"));
    User receiver = tem.persist(User.createUser("receiver@test.com", "12345678", "수신자"));
    User thirdUser = tem.persist(User.createUser("third@test.com", "12345678", "제3자"));

    targetConversation = tem.persist(Conversation.createConversation(sender, receiver));
    otherConversation = tem.persist(Conversation.createConversation(sender, thirdUser));

    for (int i = 1; i <= 5; i++) {
      DirectMessage message = DirectMessage.createMessage(targetConversation, sender, receiver, "메시지 " + i);
      tem.persistAndFlush(message);
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        throw new IllegalStateException("테스트 데이터 생성 중 인터럽트 발생으로 중단됨", e);
      }
    }

    for (int i = 1; i <= 2; i++) {
      DirectMessage message = DirectMessage.createMessage(otherConversation, sender, thirdUser, "메시지 " + i);
      tem.persistAndFlush(message);
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        throw new IllegalStateException("테스트 데이터 생성 중 인터럽트 발생으로 중단됨", e);
      }
    }

    tem.clear();
  }

  @Test
  @DisplayName("countByConversationId: 특정 대화방의 메시지 개수만 정확히 조회한다.")
  void countByConversationId_success() {
    // when
    long targetCount = directMessageRepository.countByConversationId(targetConversation.getId());
    long otherCount = directMessageRepository.countByConversationId(otherConversation.getId());

    // then
    assertThat(targetCount).isEqualTo(5L);
    assertThat(otherCount).isEqualTo(2L);
  }

  @Test
  @DisplayName("findDirectMessageByCursor: 첫 페이지 조회 시 커서 없이 limit+1개를 최신순으로 가져온다.")
  void findDirectMessageByCursor_first_page() {
    // given
    DirectMessageCursorRequest request = mock(DirectMessageCursorRequest.class);
    given(request.limit()).willReturn(3);
    given(request.idAfter()).willReturn(null);

    // when
    List<DirectMessage> result = directMessageRepository.findDirectMessageByCursor(
        targetConversation.getId(), request, null);

    // then
    assertThat(result).hasSize(4); // limit(3) + 1 반환 확인
    assertThat(result.get(0).getContent()).isEqualTo("메시지 5");

    // fetchJoin 검증 (N+1 방지 - Lazy 로딩이어도 조회가 되어야 함)
    assertThat(result.get(0).getSender().getName()).isEqualTo("발신자");
  }

  @Test
  @DisplayName("findDirectMessageByCursor: 커서(시간+ID)가 주어지면 해당 커서 이전의 데이터를 정상적으로 가져온다.")
  void findDirectMessageByCursor_next_page() {
    // given
    // 첫 번째 페이지
    DirectMessageCursorRequest firstRequest = mock(DirectMessageCursorRequest.class);
    given(firstRequest.limit()).willReturn(2);
    List<DirectMessage> firstPage = directMessageRepository.findDirectMessageByCursor(
        targetConversation.getId(), firstRequest, null);

    DirectMessage lastItemOfFirstPage = firstPage.get(1);
    Instant cursorTime = lastItemOfFirstPage.getCreatedAt();

    // 두 번째 페이지
    DirectMessageCursorRequest nextRequest = mock(DirectMessageCursorRequest.class);
    given(nextRequest.limit()).willReturn(2);
    given(nextRequest.idAfter()).willReturn(lastItemOfFirstPage.getId());

    // when
    List<DirectMessage> result = directMessageRepository.findDirectMessageByCursor(
        targetConversation.getId(), nextRequest, cursorTime);

    // then
    assertThat(result).isNotEmpty();
    assertThat(result.get(0).getContent()).isEqualTo("메시지 3");

    // 커서 타임과 같거나 작아야 함을 검증
    assertThat(result.get(0).getCreatedAt()).isBeforeOrEqualTo(cursorTime);
  }

}
