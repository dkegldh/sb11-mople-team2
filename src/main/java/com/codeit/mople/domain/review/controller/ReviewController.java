package com.codeit.mople.domain.review.controller;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.review.controller.api.ReviewApi;
import com.codeit.mople.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition;
import com.codeit.mople.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.mople.domain.review.dto.response.ReviewCursorResponse;
import com.codeit.mople.domain.review.dto.response.ReviewResponse;
import com.codeit.mople.domain.review.service.ReviewService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController implements ReviewApi {

  private final ReviewService reviewService;

  @PostMapping
  public ResponseEntity<ReviewResponse> create(
      @AuthenticationPrincipal(errorOnInvalidType = true) CustomUserDetails userDetails,
      @Valid @RequestBody ReviewCreateRequest request
  ) {
    ReviewResponse response = reviewService.create(userDetails.getUserId(), request);

    return ResponseEntity
        .created(URI.create("/api/reviews/" + response.id()))
        .body(response);
  }

  @GetMapping
  public ResponseEntity<ReviewCursorResponse> findAll(
      @Valid @ModelAttribute ReviewQueryCondition condition
  ) {
    ReviewCursorResponse response = reviewService.findAll(condition);

    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{reviewId}")
  public ResponseEntity<ReviewResponse> update(
      @AuthenticationPrincipal(errorOnInvalidType = true) CustomUserDetails userDetails,
      @PathVariable UUID reviewId,
      @Valid @RequestBody ReviewUpdateRequest request
  ) {
    ReviewResponse response = reviewService.update(reviewId, request, userDetails.getUserId());

    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{reviewId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal(errorOnInvalidType = true) CustomUserDetails userDetails,
      @PathVariable UUID reviewId
  ) {
    reviewService.delete(reviewId, userDetails.getUserId());

    return ResponseEntity.noContent().build();
  }

}
