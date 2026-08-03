package com.codeit.mople.domain.directmessage.repository;

import static com.codeit.mople.domain.directmessage.entity.QDirectMessage.directMessage;

import com.codeit.mople.domain.directmessage.dto.request.DirectMessageCursorRequest;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DirectMessageRepositoryImpl implements DirectMessageRepositoryCustom{

  private final JPAQueryFactory queryFactory;

  @Override
  public List<DirectMessage> findDirectMessageByCursor(UUID conversationId,
      DirectMessageCursorRequest request, Instant cursorTime) {
    return queryFactory
        .selectFrom(directMessage)
        .leftJoin(directMessage.sender).fetchJoin()
        .leftJoin(directMessage.receiver).fetchJoin()
        .where(
            directMessage.conversation.id.eq(conversationId),
            cursorCondition(cursorTime, request.idAfter())
        )
        .limit(request.limit() + 1)
        .orderBy(
            directMessage.createdAt.desc(),
            directMessage.id.desc()
        )
        .fetch();
  }

  private BooleanExpression cursorCondition(Instant cursorTime, UUID idAfter) {
    if (cursorTime == null) {
      return null;
    }

    if (idAfter != null) {
      return directMessage.createdAt.lt(cursorTime)
          .or(directMessage.createdAt.eq(cursorTime).and(directMessage.id.lt(idAfter)));
    }

    return directMessage.createdAt.lt(cursorTime);
  }
}
