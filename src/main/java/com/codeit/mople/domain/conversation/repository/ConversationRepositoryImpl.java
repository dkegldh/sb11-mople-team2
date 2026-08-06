package com.codeit.mople.domain.conversation.repository;

import static com.codeit.mople.domain.conversation.entity.QConversation.conversation;
import static com.codeit.mople.domain.directmessage.entity.QDirectMessage.directMessage;

import com.codeit.mople.domain.conversation.dto.request.ConversationCursorRequest;
import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.directmessage.entity.QDirectMessage;
import com.codeit.mople.domain.user.entity.QUser;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class ConversationRepositoryImpl implements ConversationRepositoryCustom{

  private final JPAQueryFactory queryFactory;

  @Override
  public List<Conversation> findConversationByCursor(
      UUID requesterId,
      ConversationCursorRequest request,
      Instant cursorTime) {

    // 묵시적 INNER JOIN 방지를 위한 명시적 Q 클래스 별칭 생성
    QDirectMessage lastMessage = new QDirectMessage("lastMessage");
    QUser sender = new QUser("sender");

    return queryFactory
        .selectFrom(conversation)
        .leftJoin(conversation.userA).fetchJoin()
        .leftJoin(conversation.userB).fetchJoin()
        // 빈 방 누락 방지를 위해 별칭을 주어 명시적 LEFT JOIN 결합 보장
        .leftJoin(conversation.lastMessage, lastMessage).fetchJoin()
        // DTO에서 상대방(sender) 정보 스냅샷 매핑 처리를 위해 Fetch Join 적용
        .leftJoin(lastMessage.sender, sender).fetchJoin()
        .where(
            isMyConversation(requesterId),
            containsKeyword(request.keywordLike(), requesterId),
            cursorCondition(cursorTime, request.idAfter())
        )
        .limit(request.limit() + 1)
        .orderBy(
            // 1순위: 마지막 메시지 시간 최신순 고정
            // lastMessage가 null이면 대화방 자체의 생성 시간을 기준으로 내림차순
            conversation.lastMessageAt.desc(),
            conversation.id.desc() // 2순위: 동시간 충돌 방지용 PK 내림차순
        )
        .fetch();
  }

  // 내가 참여한 대화방 필터링
  private BooleanExpression isMyConversation(UUID requesterId) {
    return conversation.userA.id.eq(requesterId)
        .or(conversation.userB.id.eq(requesterId));
  }

  // 상대방의 닉네임 OR 대화 내용 검색
  private BooleanExpression containsKeyword(String keywordLike, UUID requesterId) {
    // 문자열이 null인지, 빈 문자열인지 공백만 있는지를 한 번에 체크해서 false이면 해당 조건을 무시하도록 구현
    if (!StringUtils.hasText(keywordLike)) {
      return null;
    }

    // 조건1. 내가 UserB일 때 UserA의 닉네임 검색
    BooleanExpression searchUserA = conversation.userB.id.eq(requesterId)
        .and(conversation.userA.name.containsIgnoreCase(keywordLike));

    // 조건2. 내가 UserA일 때 UserB의 닉네임 검색
    BooleanExpression searchUserB = conversation.userA.id.eq(requesterId)
        .and(conversation.userB.name.containsIgnoreCase(keywordLike));

    // 조건3. 메시지 내용 중에 키워드 포함 여부
    // EXISTS 서브 쿼리를 태워 쿼리 성능 최적화
    BooleanExpression containsMessage = JPAExpressions
        .selectOne()
        .from(directMessage)
        .where(
            directMessage.conversation.eq(conversation),
            directMessage.content.containsIgnoreCase(keywordLike)
        ).exists();

    return searchUserA.or(searchUserB).or(containsMessage);
  }

  private BooleanExpression cursorCondition(Instant cursorTime, UUID idAfter) {
    if (cursorTime == null) {
      return null;
    }

    if (idAfter != null) {
      return conversation.lastMessageAt.lt(cursorTime)
          .or(conversation.lastMessageAt.eq(cursorTime).and(conversation.id.lt(idAfter)));
    }

    return conversation.lastMessageAt.lt(cursorTime);
  }
}
