package com.codeit.mople.domain.review.repository.querydsl;

import static com.codeit.mople.domain.review.entity.QReview.review;

import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition;
import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition.ReviewSortBy;
import com.codeit.mople.domain.review.entity.Review;
import com.codeit.mople.domain.review.exception.ReviewErrorCode;
import com.codeit.mople.domain.review.exception.ReviewException;
import com.codeit.mople.global.dto.SortDirection;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewCustomRepository {

  private final JPAQueryFactory queryFactory;

  @Override
  public List<Review> findAll(ReviewQueryCondition condition) {
    return queryFactory.selectFrom(review)
        .join(review.author)
        .fetchJoin()
        .join(review.content)
        .fetchJoin()
        .where(
            contentIdEqual(condition.contentId()),
            cursorCondition(condition)
        )
        .orderBy(sort(condition))
        .limit(condition.limit() + 1)
        .fetch();
  }

  @Override
  public long count(ReviewQueryCondition condition) {
    Long count = queryFactory
        .select(review.count())
        .from(review)
        .where(
            contentIdEqual(condition.contentId())
        )
        .fetchOne(); // Long 타입으로 변환(결과가 하나만 나옴, fetch로 하면 List<Long>이 되어버림)

    return count == null ? 0L : count;
  }

  // WHERE 절
  private BooleanExpression contentIdEqual(UUID contentId) {
    if (contentId == null) {
      return null;
    }

    return review.content.id.eq(contentId);
  }

  private BooleanExpression cursorCondition(ReviewQueryCondition condition) {

    // cursor가 공백일 경우 예외 발생
    if (condition.cursor() != null && condition.cursor().isBlank()) {
      throw new ReviewException(
          ReviewErrorCode.REVIEW_INVALID_CURSOR,
          Map.of("cursor", condition.cursor())
      );
    }

    boolean hasCursor = condition.cursor() != null;
    boolean hasIdAfter = condition.idAfter() != null;

    if (hasCursor != hasIdAfter) {
      throw new ReviewException(
          ReviewErrorCode.REVIEW_INVALID_CURSOR,
          Map.of(
              "cursor", condition.cursor() != null ? condition.cursor() : "null",
              "idAfter", condition.idAfter() != null ? condition.idAfter().toString() : "null"
          )
      );
    }

    if (!hasCursor) {
      return null;
    }

    // 생성순 조건
    if (condition.sortBy() == ReviewSortBy.CREATED_AT) {
      // 생성 시간은 Instant 타입(BaseEntity 참조)
      Instant cursor;

      try {
        cursor = Instant.parse(condition.cursor());
      } catch (DateTimeParseException e) {
        throw new ReviewException(
            ReviewErrorCode.REVIEW_INVALID_CURSOR,
            Map.of("cursor", condition.cursor())
        );
      }

      // 경우 1 : 생성순 오름차순(=오래된 순)
      if (condition.sortDirection() == SortDirection.ASCENDING) {
        return review.createdAt.gt(cursor)
            .or(review.createdAt.eq(cursor)
                .and(review.id.gt(condition.idAfter()))
            );
      }

      // 경우 2 : 생성순 내림차순
      return review.createdAt.lt(cursor)
          .or(review.createdAt.eq(cursor)
              .and(review.id.gt(condition.idAfter()))
          );
    }

    // 평점순 조건
    if (condition.sortBy() == ReviewSortBy.RATING) {
      // 평점은 double 타입
      Double cursor;

      try {
        cursor = Double.parseDouble(condition.cursor());
      } catch (NumberFormatException e) {
        throw new ReviewException(
            ReviewErrorCode.REVIEW_INVALID_CURSOR,
            Map.of("cursor", condition.cursor())
        );
      }

      // "NaN", "Infinity", 등의 유효하지 않은 값이 들어올 경우 예외 처리
      if (!Double.isFinite(cursor)) {
        throw new ReviewException(
            ReviewErrorCode.REVIEW_INVALID_CURSOR,
            Map.of("cursor", condition.cursor())
        );
      }

      // 경우 3 : 평점순 오름차순
      if (condition.sortDirection() == SortDirection.ASCENDING) {
        return review.rating.gt(cursor)
            .or(review.rating.eq(cursor)
                .and(review.id.gt(condition.idAfter()))
            );
      }

      // 경우 4 : 평점순 내림차순
      return review.rating.lt(cursor)
          .or(review.rating.eq(cursor)
              .and(review.id.gt(condition.idAfter()))
          );
    }

    return null;
  }

  // ORDER BY 절
  private OrderSpecifier<?>[] sort(ReviewQueryCondition condition) {
    // 오름차순, 내림차순 정의
    Order order = condition.sortDirection()
        == SortDirection.ASCENDING ?
        Order.ASC : Order.DESC;

    // 생성순일 경우
    if (condition.sortBy() == ReviewSortBy.CREATED_AT) {
      return new OrderSpecifier[]{
          new OrderSpecifier<>(order, review.createdAt),
          new OrderSpecifier<>(Order.ASC, review.id) // tie-breaker
      };
    }

    // 평점순일 경우
    return new OrderSpecifier[]{
        new OrderSpecifier<>(order, review.rating),
        new OrderSpecifier<>(Order.ASC, review.id) // tie-breaker
    };
  }

}
