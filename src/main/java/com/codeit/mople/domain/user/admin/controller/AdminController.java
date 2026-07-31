package com.codeit.mople.domain.user.admin.controller;

import com.codeit.mople.domain.user.admin.controller.api.AdminApi;
import com.codeit.mople.domain.user.admin.dto.LockUpdateRequest;
import com.codeit.mople.domain.user.admin.dto.RoleUpdateRequest;
import com.codeit.mople.domain.user.admin.service.AdminService;
import com.codeit.mople.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AdminController implements AdminApi {

  private final AdminService adminService;

  @Override
  @PatchMapping("/{userId}/role")
  public ApiResponse<Void> changeRole(
      @PathVariable UUID userId,
      @Valid @RequestBody RoleUpdateRequest request) {
    adminService.changeUserRole(userId, request.role());
    return ApiResponse.success();
  }

  @Override
  @PatchMapping("/{userId}/locked")
  public ApiResponse<Void> changeLocked(
      @PathVariable UUID userId,
      @Valid @RequestBody LockUpdateRequest request) {
    adminService.changeUserLocked(userId, request.locked());
    return ApiResponse.success();
  }
}
