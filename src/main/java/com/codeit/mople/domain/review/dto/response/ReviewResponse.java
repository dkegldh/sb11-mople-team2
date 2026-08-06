package com.codeit.mople.domain.review.dto.response;

import com.codeit.mople.domain.review.entity.Review;
import com.codeit.mople.global.dto.UserSummary;
import java.util.UUID;

public record ReviewResponse(
    UUID id,
    UUID contentId,
    UserSummary author,
    String text,
    double rating
) {

  public static ReviewResponse from(Review review) {
    return new ReviewResponse(
        review.getId(),
        review.getContent().getId(),
        new UserSummary(
            review.getAuthor().getId(),
            review.getAuthor().getName(),
            review.getAuthor().getProfileImageUrl()
        ),
        review.getText(),
        review.getRating()
    );
  }

}
