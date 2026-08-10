package com.codeit.mople.domain.review.converter;

import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition.ReviewSortBy;
import com.codeit.mople.domain.review.exception.ReviewErrorCode;
import com.codeit.mople.domain.review.exception.ReviewException;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ReviewSortByConverter implements Converter<String, ReviewSortBy> {

  @Override
  public ReviewSortBy convert(String value) {

    try {
      return ReviewSortBy.from(value);
    } catch (IllegalArgumentException e) {
      throw new ReviewException(
          ReviewErrorCode.REVIEW_INVALID_SORT_BY,
          Map.of("sortBy", value)
      );
    }

  }

}
