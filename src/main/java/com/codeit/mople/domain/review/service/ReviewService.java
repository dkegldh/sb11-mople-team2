package com.codeit.mople.domain.review.service;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.follow.event.FolloweeActivityEvent;
import com.codeit.mople.domain.follow.service.FollowService;
import com.codeit.mople.domain.content.exception.ContentErrorCode;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition;
import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition.ReviewSortBy;
import com.codeit.mople.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.mople.domain.review.dto.response.ReviewCursorResponse;
import com.codeit.mople.domain.review.dto.response.ReviewResponse;
import com.codeit.mople.domain.review.entity.Review;
import com.codeit.mople.domain.review.exception.ReviewErrorCode;
import com.codeit.mople.domain.review.exception.ReviewException;
import com.codeit.mople.domain.review.repository.ReviewRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.exception.UserException;
import com.codeit.mople.domain.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final UserRepository userRepository;
  private final ContentRepository contentRepository;
  private final FollowService followService;
  private final ApplicationEventPublisher publisher;

  @Transactional
  public ReviewResponse create(UUID authorId, ReviewCreateRequest request) {

    log.debug("리뷰 생성 시도: authorId={}, contentId={}, rating={}",
        authorId, request.contentId(), request.rating());

    User author = userRepository.findById(authorId).orElseThrow(() ->
        new UserException(UserErrorCode.USER_NOT_FOUND)
    );

    Content content = contentRepository.findById(request.contentId()).orElseThrow(() ->
        new ContentException(ContentErrorCode.CONTENT_NOT_FOUND)
    );

    Review review = Review.create(content, author, request.text(), request.rating());

    Review savedReview = reviewRepository.save(review);

    followService.getFollowerIds(authorId)
        .forEach(followerId -> publisher.publishEvent(
            new FolloweeActivityEvent(authorId, author.getName(), "리뷰를 작성했습니다.", followerId)));

    // TODO 김명근: 동시성 문제(Race Condition)는 다음 스프린트 기간 때 락 사용 등을 활용하여 개선
    // 콘텐츠의 리뷰 개수, 평균 평점을 조회
    long reviewCount = reviewRepository.countByContentId(content.getId());
    Double averageRating = reviewRepository.findAverageRatingByContentId(content.getId());

    content.updateRatingStats(averageRating, (int) reviewCount);

    ReviewResponse response = ReviewResponse.from(savedReview);
    log.info("리뷰 생성 완료: reviewId={}, authorId={}, contentId={}",
        savedReview.getId(), authorId, request.contentId());

    return response;
  }

  @Transactional(readOnly = true)
  public ReviewCursorResponse findAll(ReviewQueryCondition condition) {

    log.debug("리뷰 목록 조회 시도: contentId={}, cursor={}, idAfter={},"
            + "limit={}, sortBy={}, sortDirection={}",
        condition.contentId(),
        condition.cursor(),
        condition.idAfter(),
        condition.limit(),
        condition.sortBy(),
        condition.sortDirection()
    );

    // limit + 1만큼 조회
    List<Review> reviews = reviewRepository.findAll(condition);

    // 다음 페이지 존재 여부 확인
    boolean hasNext = reviews.size() > condition.limit();

    // 응답용 데이터만 limit 개수 유지
    List<Review> pageReviews = hasNext ?
        new ArrayList<>(reviews.subList(0, condition.limit())) : reviews;

    List<ReviewResponse> data = pageReviews.stream()
        .map(ReviewResponse::from)
        .toList();

    long totalCount = reviewRepository.count(condition);

    // 임시 커서, 보조 커서 초기화
    String nextCursor = null;
    UUID nextIdAfter = null;

    // 마지막 원소 조회
    if (hasNext && !pageReviews.isEmpty()) {
      Review lastReview = pageReviews.get(pageReviews.size() - 1);

      nextIdAfter = lastReview.getId();

      // 정렬 조건
      if (condition.sortBy() == ReviewSortBy.CREATED_AT) {
        nextCursor = lastReview.getCreatedAt().toString();
      } else if (condition.sortBy() == ReviewSortBy.RATING) {
        nextCursor = String.valueOf(lastReview.getRating());
      }
    }

    log.info("리뷰 목록 조회 완료: size={}, totalCount={}, hasNext={}, nextCursor={}, nextIdAfter={}",
        data.size(), totalCount, hasNext, nextCursor, nextIdAfter);

    return new ReviewCursorResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        condition.sortBy(),
        condition.sortDirection()
    );
  }

  @Transactional
  public ReviewResponse update(UUID reviewId, ReviewUpdateRequest request, UUID authorId) {

    log.debug("리뷰 수정 시도: reviewId={}, authorId={}, rating={}",
        reviewId, authorId,request.rating());

    Review review = reviewRepository.findById(reviewId).orElseThrow(() ->
        new ReviewException(ReviewErrorCode.REVIEW_NOT_FOUND)
    );

    validateAuthor(review, authorId);

    if (request.text() != null) {
      review.updateText(request.text());
    }

    if (request.rating() != null) {
      review.updateRating(request.rating());
    }

    Content content = review.getContent();

    // TODO 김명근: 동시성 문제(Race Condition)는 다음 스프린트 기간 때 락 사용 등을 활용하여 개선
    // 콘텐츠의 평균 평점을 조회
    // 리뷰 내용만 변경 된 경우 계산하지 않음
    if (request.rating() != null) {
      Double averageRating = reviewRepository.findAverageRatingByContentId(content.getId());

      content.updateRatingStats(averageRating, content.getReviewCount());
    }

    ReviewResponse response = ReviewResponse.from(review);

    log.info("리뷰 수정 완료: reviewId={}, authorId={}, contentId={}, rating={}",
        reviewId, authorId, content.getId(), request.rating());

    return response;
  }

  @Transactional
  public void delete(UUID reviewId, UUID authorId) {

    log.debug("리뷰 삭제 시도: reviewId={}, authorId={}",
        reviewId, authorId);

    Review review = reviewRepository.findById(reviewId).orElseThrow(() ->
        new ReviewException(ReviewErrorCode.REVIEW_NOT_FOUND)
    );

    validateAuthor(review, authorId);

    Content content = review.getContent();

    reviewRepository.delete(review);

    long reviewCount = reviewRepository.countByContentId(content.getId());
    Double averageRating = reviewRepository.findAverageRatingByContentId(content.getId());

    Double updateAverageRating = reviewCount == 0 ? 0.0 : averageRating;

    // 리뷰 삭제 후 리뷰가 0개일 때 평균 평점을 0점으로(averageRating null 방지)
    content.updateRatingStats(
        updateAverageRating,
        (int) reviewCount
    );

    log.info("리뷰 삭제 완료: reviewId={}, authorId={}, contentId={}, averageRating={}, reviewCount={}",
        reviewId, authorId, content.getId(), updateAverageRating, reviewCount);

  }

  private void validateAuthor(Review review, UUID authorId) {
    if (!review.getAuthor().getId().equals(authorId)) {
      throw new ReviewException(ReviewErrorCode.REVIEW_FORBIDDEN);
    }
  }

}
