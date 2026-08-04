package com.codeit.mople.domain.follow.controller;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.follow.controller.api.FollowApi;
import com.codeit.mople.domain.follow.dto.FollowRequest;
import com.codeit.mople.domain.follow.dto.FollowResponse;
import com.codeit.mople.domain.follow.service.FollowService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/follows")
public class FollowController implements FollowApi {

  private final FollowService followService;

  @Override
  @PostMapping
  public ResponseEntity<FollowResponse> createFollow(
      @AuthenticationPrincipal CustomUserDetails principal,
      @Valid @RequestBody FollowRequest followRequest) {
    return ResponseEntity.status(HttpStatus.CREATED).body(followService.follow(followRequest, principal.getUserId()));
  }

  @Override
  @DeleteMapping("/{followId}")
  public ResponseEntity<Void> cancelFollow(
      @AuthenticationPrincipal CustomUserDetails principal,
      @PathVariable UUID followId) {
    followService.unFollow(followId, principal.getUserId());
    return ResponseEntity.noContent().build();
  }

  @Override
  @GetMapping("/followed-by-me")
  public ResponseEntity<FollowResponse> isFollowedByMe(
      @AuthenticationPrincipal CustomUserDetails principal,
      @RequestParam UUID followeeId) {
    return ResponseEntity.ok(followService.getFollowByMe(followeeId, principal.getUserId()));
  }

  @Override
  @GetMapping("/count")
  public ResponseEntity<Long> getFollowerCount(
      @RequestParam UUID followeeId) {
    return ResponseEntity.ok(followService.getFollowCount(followeeId));
  }
}
