package com.codeit.mople.domain.content.repository;

import static com.codeit.mople.domain.content.entity.QContent.content;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentSortBy;
import com.codeit.mople.domain.content.entity.ContentType;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
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
  public List<Content> findContentByCursor(UUID cursorId, Object parsedCursorValue,
      int limit, ContentType type,  List<UUID> contentIds, ContentSortBy sortBy) {
    return queryFactory.selectFrom(content)
        .where(
            typeCondition(type), //카테고리 동적 필터
            idCondition(contentIds), //검색어 동적 필터
            cursorCondition(cursorId, parsedCursorValue, sortBy) //정렬 기준별 커서 동적 조건 (수정됨)
        )
        .orderBy(orderSpecifiers(sortBy)) //동적 OrderBy
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

  //분류(type)별 데이터 개수 조회 메서드
  public long countContentsByTypeAndIds(ContentType type, List<UUID> contentIds) {
    Long count = queryFactory.select(content.count())
        .from(content)
        .where(
            typeCondition(type),
            idCondition(contentIds)
        )
        .fetchOne();
    return count != null ? count : 0L;
  }

  //카테고리 필터링 조건
  private BooleanExpression typeCondition(ContentType type) {
    return type != null ? content.type.eq(type) : null;
  }

  //검색어 필터링 조건(Elasticsearch Document 활용)
  private BooleanExpression idCondition(List<UUID> contentIds) {
    return contentIds == null ? null : content.id.in(contentIds);
  }

  // 커서 필터링 조건(Service 계층에서 이미 타입 검증/파싱된 값을 받음)
  private BooleanExpression cursorCondition(UUID cursorId, Object parsedCursorValue, ContentSortBy sortBy) {
    if (cursorId == null || parsedCursorValue == null) return null;

    if (sortBy == ContentSortBy.WATCHER_COUNT) {
      long count = (Long) parsedCursorValue;
      return content.watcherCount.lt(count).or(content.watcherCount.eq(count).and(content.id.gt(cursorId)));
    } else if (sortBy == ContentSortBy.RATING) {
      double rating = (Double) parsedCursorValue;
      NumberExpression<Double> averageRating = averageRatingExpression();
      return averageRating.lt(rating).or(averageRating.eq(rating).and(content.id.gt(cursorId)));
    } else { // CREATED_AT
      Instant time = (Instant) parsedCursorValue;
      return content.createdAt.lt(time).or(content.createdAt.eq(time).and(content.id.gt(cursorId)));
    }
  }

  // 동적 정렬 조건 메서드
  private OrderSpecifier<?>[] orderSpecifiers(ContentSortBy sortBy) {
    if (sortBy == ContentSortBy.WATCHER_COUNT) {
      return new OrderSpecifier<?>[]{content.watcherCount.desc().nullsLast(), content.id.asc()};
    } else if (sortBy == ContentSortBy.RATING) {
      NumberExpression<Double> averageRating = averageRatingExpression();
      return new OrderSpecifier<?>[]{averageRating.desc().nullsLast(), content.id.asc()};
    } else { // CREATED_AT
      return new OrderSpecifier<?>[]{content.createdAt.desc().nullsLast(), content.id.asc()};
    }
  }

  private NumberExpression<Double> averageRatingExpression() {
    return new CaseBuilder()
        .when(content.reviewCount.eq(0L))
        .then(0.0)
        .otherwise(
            content.ratingSum.divide(content.reviewCount.doubleValue())
        );
  }
}