package com.codeit.mople.domain.user.admin.service;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.error.CustomException;
import com.codeit.mople.global.event.UserForceLogoutEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public void changeUserRole(UUID userId, String roleStr) {
    validateNotSelf(userId);
    log.debug("권한 변경 시작 - userId: {}, role: {}", userId, roleStr);
    Role role = Role.valueOf(roleStr.toUpperCase());
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    Role previousRole = user.getRole();
    user.changeRole(role);
    if (previousRole != role) {
      eventPublisher.publishEvent(new UserForceLogoutEvent(userId));
    }
    log.info("권한 변경 완료 - userId: {}, role: {}", userId, role);
  }

  @Transactional
  public void changeUserLocked(UUID userId, boolean locked) {
    validateNotSelf(userId);
    log.debug("계정 잠금 변경 시작 - userId: {}, locked: {}", userId, locked);
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    if (locked) {
      user.lock();
    } else {
      user.unlock();
    }
    eventPublisher.publishEvent(new UserForceLogoutEvent(userId));
    log.info("계정 잠금 변경 완료 - userId: {}, locked: {}", userId, locked);
  }

  private void validateNotSelf(UUID targetUserId) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    CustomUserDetails principal = (CustomUserDetails) auth.getPrincipal();
    if (targetUserId.equals(principal.getUserId())) {
      throw new CustomException(UserErrorCode.CANNOT_MODIFY_SELF);
    }
  }
}
