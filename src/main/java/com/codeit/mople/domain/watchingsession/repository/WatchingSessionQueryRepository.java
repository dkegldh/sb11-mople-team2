package com.codeit.mople.domain.watchingsession.repository;

import static com.codeit.mople.domain.watchingsession.entity.QWatchingSession.watchingSession;

import com.codeit.mople.domain.watchingsession.entity.WatchingSession;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WatchingSessionQueryRepository {
  private final JPAQueryFactory queryFactory;

  //커서 기반 조회
  public List<WatchingSession> findSessionByCursor(
      UUID contentId, String watcherNameLike, String cursor, UUID ifAfter,
      int limit, String sortBy, String sortDirection) {
    return queryFactory.selectFrom(watchingSession)
        .join(watchingSession.user).fetchJoin() //N+1 문제 방지(유저 정보 함께 로드)
        .join(watchingSession.content).fetchJoin() //N+1 문제 방지(콘텐츠 정보 함께 로드)
        .where(
            watchingSession.content.id.eq(contentId),
            watcherNameContains(watcherNameLike),
            cursorCondition(cursor, ifAfter, sortDirection)
        )
        .orderBy(sortCondition(sortDirection), watchingSession.id.asc())
        .limit(limit + 1)
        .fetch();
  }

  //전체 데이터 수 조회(totalCount용)
  public long countSessions(UUID contentId, String watcherNameLike) {
    Long count = queryFactory.select(watchingSession.count())
        .from(watchingSession)
        .where(
            watchingSession.content.id.eq(contentId),
            watcherNameContains(watcherNameLike)
        ).fetchOne();

    return count != null ? count : 0L;
  }

  //검색 조건: 시청자 이름 포함 여부
  private BooleanExpression watcherNameContains(String watcherNameLike) {
    if (watcherNameLike == null || watcherNameLike.trim().isEmpty()) {
      return null;
    }
    return watchingSession.user.name.containsIgnoreCase(watcherNameLike);
  }

  //커서 조건: sortDirection에 따라 비교 연산 달라짐
  private BooleanExpression cursorCondition(String cursor, UUID idAfter, String sortDirection) {
    if (cursor == null || idAfter == null) {
      return null;
    }

    Instant cursorTime = Instant.parse(cursor);

    if ("DESCENDING".equalsIgnoreCase(sortDirection)) {
      //내림차순
      return watchingSession.createdAt.lt(cursorTime)
          .or(watchingSession.createdAt.eq(cursorTime).and(watchingSession.id.gt(idAfter)));
    } else {
      //오름차순
      return watchingSession.createdAt.gt(cursorTime)
          .or(watchingSession.createdAt.eq(cursorTime).and(watchingSession.id.gt(idAfter)));
    }
  }

  //정렬 조건
  private OrderSpecifier<?> sortCondition(String sortDirection) {
    if ("DESCENDING".equalsIgnoreCase(sortDirection)) {
      return watchingSession.createdAt.desc();
    }
    return watchingSession.createdAt.asc();
  }
}
