package com.codeit.mople.domain.conversation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.codeit.mople.domain.conversation.dto.request.ConversationCursorRequest;
import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.config.JpaAuditingConfig;
import com.codeit.mople.global.config.QueryDslConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
public class ConversationRepositoryTest {

  @Autowired
  private ConversationRepository conversationRepository;

  @Autowired
  private TestEntityManager tem;

  private User me;
  private User user1;

  private Conversation myConvWithUser1;
  private Conversation myConvWithUser2;

  @BeforeEach
  void setup() {
    me = tem.persist(User.createUser("me@test.com", "12345678", "내이름"));
    user1 = tem.persist(User.createUser("u1@test.com", "12345678", "유저1"));
    User user2 = tem.persist(User.createUser("u2@test.com", "12345678", "유저2"));

    myConvWithUser1 = tem.persist(Conversation.createConversation(me, user1));
    myConvWithUser2 = tem.persist(Conversation.createConversation(me, user2));
    Conversation otherConv = tem.persist(Conversation.createConversation(user1, user2));

    DirectMessage m1 = DirectMessage.createMessage(myConvWithUser1, me, user1, "메시지");
    tem.persistAndFlush(m1);
    myConvWithUser1.updateLastMessage(m1);
    tem.persistAndFlush(myConvWithUser1);

    DirectMessage m2 = DirectMessage.createMessage(myConvWithUser2, user2, me, "문자");
    tem.persistAndFlush(m2);
    myConvWithUser2.updateLastMessage(m2);
    tem.persistAndFlush(myConvWithUser2);

    tem.clear();
  }

  @Test
  @DisplayName("countByParticipantId: 내가 참여한 대화방의 개수만 정확하게 카운트한다.")
  void countByParticipantId_success() {
    // when
    long myCount = conversationRepository.countByParticipantId(me.getId());
    long user1Count = conversationRepository.countByParticipantId(user1.getId());

    // then
    assertThat(myCount).isEqualTo(2L);
    assertThat(user1Count).isEqualTo(2L);
  }

  @Test
  @DisplayName("findWithDetailsById: 식별자로 대화방 조회 시 마지막 메시지와 발신자 정보까지 Fetch Join으로 가져온다.")
  void findWithDetailsById_success() {
    // when
    Optional<Conversation> result = conversationRepository.findWithDetailsById(myConvWithUser1.getId());

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getLastMessage()).isNotNull();
    assertThat(result.get().getLastMessage().getContent()).isEqualTo("메시지");
    // Fetch Join이 걸려있으므로 Lazy 관련 예외가 터지지 않아야 함
    assertThat(result.get().getLastMessage().getSender().getName()).isEqualTo("내이름");
  }

  @Test
  @DisplayName("findByIdWithUsers: 대화방 조회 시 Fetch Join을 통해 UserA와 UserB가 프록시가 아닌 실제 엔티티로 한 번에 로딩된다.")
  void findByIdWithUsers_FetchJoinTest() {
    // given
    UUID conversationId = myConvWithUser1.getId();

    // when
    Optional<Conversation> foundConversation = conversationRepository.findByIdWithUsers(conversationId);

    // then
    assertThat(foundConversation).isPresent();
    Conversation result = foundConversation.get();

    // 인자로 들어온 JPA 엔티티가 진짜 데이터가 채워진 상태면 true, 아직 데이터가 안 불려온 가짜 프록시 상태면 false를 반환
    // Fetch Join이 정상 작동했다면 DB에서 실제 객체를 끌고 와서 true를 반환
    assertThat(Hibernate.isInitialized(result.getUserA())).isTrue();
    assertThat(Hibernate.isInitialized(result.getUserB())).isTrue();
    assertThat(result.getUserA().getName()).isNotNull();
    assertThat(result.getUserB().getName()).isNotNull();
  }

  @Test
  @DisplayName("findConversationByCursor: 커서와 검색어 없이 조회 시 내가 참여한 방만 최신순으로 가져온다.")
  void findConversationByCursor_no_keyword() {
    // given
    ConversationCursorRequest request = mock(ConversationCursorRequest.class);
    given(request.limit()).willReturn(10);
    given(request.keywordLike()).willReturn(null);

    // when
    List<Conversation> result = conversationRepository.findConversationByCursor(me.getId(), request, null);

    // then
    assertThat(result).hasSize(2);
    // 최근에 메시지가 작성된 myConvWithUser2 가 먼저 나와야 함 (lastMessageAt.desc() 정렬)
    assertThat(result.get(0).getId()).isEqualTo(myConvWithUser2.getId());
    assertThat(result.get(1).getId()).isEqualTo(myConvWithUser1.getId());
  }

  @Test
  @DisplayName("findConversationByCursor: [상대방 닉네임 검색] 내가 참여한 방 중 상대방 이름이 키워드와 일치하면 조회된다.")
  void findConversationByCursor_search_by_partner_name() {
    // given
    ConversationCursorRequest request = mock(ConversationCursorRequest.class);
    given(request.limit()).willReturn(10);
    given(request.keywordLike()).willReturn("1");

    // when
    List<Conversation> result = conversationRepository.findConversationByCursor(me.getId(), request, null);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(myConvWithUser1.getId());
  }

  @Test
  @DisplayName("findConversationByCursor: [메시지 내용 검색] 상대방 닉네임이 달라도 메시지 내용에 키워드가 있으면 조회된다.")
  void findConversationByCursor_search_by_message_content() {
    // given
    ConversationCursorRequest request = mock(ConversationCursorRequest.class);
    given(request.limit()).willReturn(10);
    given(request.keywordLike()).willReturn("문자");

    // when
    List<Conversation> result = conversationRepository.findConversationByCursor(me.getId(), request, null);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(myConvWithUser2.getId());
    assertThat(result.get(0).getLastMessage().getContent()).contains("문자");
  }

  @Test
  @DisplayName("findConversationByCursor: 페이징 시 기준 커서(시간+ID) 이전의 대화방만 정상적으로 가져온다.")
  void findConversationByCursor_pagination() {
    // given
    // 첫 번째 페이지
    ConversationCursorRequest firstRequest = mock(ConversationCursorRequest.class);
    given(firstRequest.limit()).willReturn(1);
    given(firstRequest.keywordLike()).willReturn(null);

    List<Conversation> firstPage = conversationRepository.findConversationByCursor(me.getId(), firstRequest, null);
    Conversation lastItem = firstPage.get(0);

    Instant cursorTime = lastItem.getLastMessageAt();
    UUID idAfter = lastItem.getId();

    // 두 번째 페이지
    ConversationCursorRequest nextRequest = mock(ConversationCursorRequest.class);
    given(nextRequest.limit()).willReturn(2);
    given(nextRequest.keywordLike()).willReturn(null);
    given(nextRequest.idAfter()).willReturn(idAfter);

    // when
    List<Conversation> result = conversationRepository.findConversationByCursor(me.getId(), nextRequest, cursorTime);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(myConvWithUser1.getId());
    assertThat(result.get(0).getLastMessageAt()).isBeforeOrEqualTo(cursorTime);
  }

}
