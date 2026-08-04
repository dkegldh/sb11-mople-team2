package com.codeit.mople.domain.notification.repository;

import static com.codeit.mople.domain.notification.entity.QNotification.notification;

import com.codeit.mople.domain.notification.entity.Notification;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Notification> findNotificationByCursor(UUID receiverId, Instant cursorTime,
        UUID idAfter, int limit) {
        return queryFactory
            .selectFrom(notification)
            .leftJoin(notification.receiver).fetchJoin()
            .where(
                notification.receiver.id.eq(receiverId),
                cursorCondition(cursorTime, idAfter)
            )
            .limit(limit + 1)
            .orderBy(
                notification.createdAt.desc(),
                notification.id.desc()
            )
            .fetch();
    }

    private BooleanExpression cursorCondition(Instant cursorTime, UUID idAfter) {
        if (cursorTime == null) {
            return null;
        }
        return notification.createdAt.lt(cursorTime)
            .or(notification.createdAt.eq(cursorTime).and(notification.id.lt(idAfter)));
    }
}
