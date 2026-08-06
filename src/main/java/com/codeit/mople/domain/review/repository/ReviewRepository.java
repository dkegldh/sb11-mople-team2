package com.codeit.mople.domain.review.repository;

import com.codeit.mople.domain.review.entity.Review;
import com.codeit.mople.domain.review.repository.querydsl.ReviewCustomRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends
    JpaRepository<Review, UUID>,
    ReviewCustomRepository {

  // Review 테이블에서 Review의 contentId가 받아온 contentId와 일치하는 행들의 개수를 계산(콘텐츠 리뷰 개수)
  long countByContentId(UUID contentId);

  // Review 테이블에서 Review의 특정 contentId가 받아온 contentId와 일치하는 행들의 평균 rating을 계산(콘텐츠 평점 조회)
  // null값이 될 수 있음(콘텐츠 리뷰가 하나도 없는 경우)
  @Query("""
      select avg(r.rating)
      from Review r
      where r.content.id = :contentId
      """)
  Double findAverageRatingByContentId(@Param("contentId") UUID contentId);
}
