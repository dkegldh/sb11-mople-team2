package com.codeit.mople.domain.user.controller;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.user.dto.request.ChangePasswordRequest;
import com.codeit.mople.domain.user.dto.request.UserCreateRequest;
import com.codeit.mople.domain.user.dto.request.UserSearchRequest;
import com.codeit.mople.domain.user.dto.request.UserUpdateRequest;
import com.codeit.mople.domain.user.dto.response.UserDto;
import com.codeit.mople.domain.user.service.UserService;
import com.codeit.mople.global.dto.CursorResponse;
import com.codeit.mople.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<UserDto> signUp(@Valid @RequestBody UserCreateRequest request) {
    return ApiResponse.success(userService.signUp(request));
  }

  @GetMapping("/{userId}")
  public ApiResponse<UserDto> getUser(@PathVariable UUID userId) {
    return ApiResponse.success(userService.getUser(userId));
  }

  @GetMapping
  public ApiResponse<CursorResponse<UserDto>> getUsers(UserSearchRequest request) {
    return ApiResponse.success(userService.getUsers(request));
  }

  @PatchMapping(value = "/{userId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<UserDto> updateProfile(
      @PathVariable UUID userId,
      @AuthenticationPrincipal CustomUserDetails principal,
      @Valid @ModelAttribute UserUpdateRequest request
  ) {
    return ApiResponse.success(userService.updateProfile(userId, principal.getUserId(), request));
  }

  @PatchMapping("/{userId}/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @PathVariable UUID userId,
      @AuthenticationPrincipal CustomUserDetails principal,
      @Valid @RequestBody ChangePasswordRequest request
  ) {
    userService.changePassword(userId, principal.getUserId(), request);
  }
}
