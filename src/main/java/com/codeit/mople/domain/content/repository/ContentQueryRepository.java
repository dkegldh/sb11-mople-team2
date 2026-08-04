package com.codeit.mople.domain.content.repository;

import static com.codeit.mople.domain.content.entity.QContent.content;

import com.codeit.mople.domain.content.entity.Content;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContentQueryRepository {
  private final JPAQueryFactory queryFactory;

  //커서 기반 데이터 조회 (limit + 1개)
  public List<Content> findContentByCursor(UUID cursorId, Instant cursorCreatedAt, int limit) {
    return queryFactory.selectFrom(content)
        .where(cursorCondition(cursorId, cursorCreatedAt))
        .orderBy(content.createdAt.desc(), content.id.asc())
        .limit(limit + 1)
        .fetch();
  }

  //전체 데이터 개수 조회
  public long countAllContents() {
    Long count = queryFactory.select(content.count())
        .from(content)
        .fetchOne();
    return count != null ? count : 0L;
  }

  //커서 필터링 조건
  private BooleanExpression cursorCondition(UUID cursorId, Instant cursorCreatedAt) {
    if (cursorId == null || cursorCreatedAt == null) {
      return null;
    }
    return content.createdAt.lt(cursorCreatedAt)
        .or(content.createdAt.eq(cursorCreatedAt).and(content.id.gt(cursorId)));
  }
}