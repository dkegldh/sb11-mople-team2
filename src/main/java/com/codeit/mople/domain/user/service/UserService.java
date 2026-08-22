package com.codeit.mople.domain.user.service;

import com.codeit.mople.domain.auth.repository.RefreshTokenRepository;
import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.user.dto.request.ChangePasswordRequest;
import com.codeit.mople.domain.user.dto.request.UserCreateRequest;
import com.codeit.mople.domain.user.dto.request.UserSearchRequest;
import com.codeit.mople.domain.user.dto.request.UserSortBy;
import com.codeit.mople.domain.user.dto.request.UserUpdateRequest;
import com.codeit.mople.domain.user.dto.response.UserDto;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.event.UserSearchIndexEvent;
import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.exception.UserException;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.domain.user.repository.search.UserSearchRepository;
import com.codeit.mople.global.config.CacheNames;
import com.codeit.mople.global.dto.CursorResponse;
import com.codeit.mople.global.storage.FileStorageService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final FileStorageService fileStorageService;
  private final SessionTokenRepository sessionTokenRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final UserSearchRepository searchRepository;

  private final ApplicationEventPublisher eventPublisher;

  @CacheEvict(value = CacheNames.USERS, allEntries = true)
  @Transactional
  public UserDto signUp(UserCreateRequest request) {
    String normalizedEmail = request.email().toLowerCase(Locale.ROOT);
    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new UserException(UserErrorCode.DUPLICATE_EMAIL, Map.of("email", normalizedEmail));
    }
    String encodedPassword = passwordEncoder.encode(request.password());
    User user = User.createUser(normalizedEmail, encodedPassword, request.name());
    User saved = userRepository.save(user);

    eventPublisher.publishEvent(
        new UserSearchIndexEvent(UUID.randomUUID(), saved.getId(), saved.getEmail())
    );

    return UserDto.from(saved);
  }

  @Cacheable(value = CacheNames.USERS, key = "#userId")
  public UserDto getUser(UUID userId) {
    User user = findUserOrThrow(userId);
    return UserDto.from(user);
  }

  @Cacheable(
      value = CacheNames.USERS,
      key = "{"
          + "#request.emailLike(),"
          + "#request.roleEqual(),"
          + "#request.isLocked(),"
          + "#request.cursor(),"
          + "#request.idAfter(),"
          + "#request.limitOrDefault(),"
          + "#request.sortByOrDefault(),"
          + "#request.sortDirectionOrDefault()"
          + "}"
  )
  public CursorResponse<UserDto> getUsers(UserSearchRequest request) {
    List<UUID> userIds = null;

    if (request.emailLike() != null && !request.emailLike().isBlank()) {
      userIds = searchRepository.findAllByEmailContainingIgnoreCase(
          request.emailLike()
      );
    }

    long totalCount = userRepository.countUsers(request, userIds);

    List<User> users = userRepository.searchUsers(request, userIds);

    return CursorResponse.of(
        users.stream()
            .map(UserDto::from)
            .toList(),
        request.limitOrDefault(),
        totalCount,
        request.sortByOrDefault().getValue(),
        request.sortDirectionOrDefault().name(),
        dto -> cursorValueOf(dto, request.sortByOrDefault()),
        UserDto::id
    );
  }

  @PreAuthorize("hasRole('ADMIN') or #targetUserId == authentication.principal.userId")
  @CacheEvict(value = CacheNames.USERS, allEntries = true)
  @Transactional
  public UserDto updateProfile(UUID targetUserId, UserUpdateRequest request, MultipartFile image) {
    User user = findUserOrThrow(targetUserId);

    String imageUrl = user.getProfileImageUrl(); //새롭게 반영될 URL
    final String oldImageUrl = user.getProfileImageUrl(); //삭제 예약을 위한 기존 URL 백업

    if (image != null && !image.isEmpty()) {
      //새로운 이미지 업로드 먼저 수행
      imageUrl = fileStorageService.upload(image);

      //트랜잭션 커밋 완료 후 기존 이미지 삭제 예약
      if (oldImageUrl != null) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            fileStorageService.delete(oldImageUrl);
          }
        });
      }
    }

    user.updateProfile(request.name(), imageUrl);

    return UserDto.from(user);
  }

  @PreAuthorize("hasRole('ADMIN') or #targetUserId == authentication.principal.userId")
  @Transactional
  public void changePassword(UUID targetUserId, ChangePasswordRequest request) {
    User user = findUserOrThrow(targetUserId);
    user.changePassword(passwordEncoder.encode(request.password()));
    user.destroyTemporaryPassword();
    refreshTokenRepository.invalidate(targetUserId);
    sessionTokenRepository.invalidate(targetUserId);
  }

  private User findUserOrThrow(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
  }

  private String cursorValueOf(UserDto dto, UserSortBy sortBy) {
    return switch (sortBy) {
      case NAME -> dto.name();
      case EMAIL -> dto.email();
      case CREATED_AT -> dto.createdAt().toString();
      case IS_LOCKED -> String.valueOf(dto.locked());
      case ROLE -> dto.role().name();
    };
  }
}
