package com.codeit.mople.domain.review.repository.querydsl;

import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition;
import com.codeit.mople.domain.review.entity.Review;
import java.util.List;

public interface ReviewCustomRepository {

  List<Review> findAll(ReviewQueryCondition condition);

  long count(ReviewQueryCondition condition);

}
