package com.codeit.mople.domain.user.service;

import com.codeit.mople.domain.user.dto.request.ChangePasswordRequest;
import com.codeit.mople.domain.user.dto.request.UserCreateRequest;
import com.codeit.mople.domain.user.dto.request.UserSearchRequest;
import com.codeit.mople.domain.user.dto.request.UserSortBy;
import com.codeit.mople.domain.user.dto.request.UserUpdateRequest;
import com.codeit.mople.domain.user.dto.response.UserDto;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.exception.UserException;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.dto.CursorResponse;
import com.codeit.mople.global.storage.FileStorageService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final FileStorageService fileStorageService;

  @Transactional
  public UserDto signUp(UserCreateRequest request) {
    if(userRepository.existsByEmail(request.email())) {
      throw new UserException(UserErrorCode.DUPLICATE_EMAIL, Map.of("email", request.email()));
    }
    String encodedPassword = passwordEncoder.encode(request.password());
    User user = User.createUser(request.email(), encodedPassword, request.name());
    User saved = userRepository.save(user);
    return UserDto.from(saved);
  }

  public UserDto getUser(UUID userId) {
    User user = findUserOrThrow(userId);
    return UserDto.from(user);
  }

  public CursorResponse<UserDto> getUsers(UserSearchRequest request) {
    List<User> users = userRepository.searchUsers(request);

    return CursorResponse.of(
        users.stream().map(UserDto::from).toList(),
        request.limitOrDefault(),
        request.sortByOrDefault().name(),
        request.sortDirectionOrDefault().name(),
        dto -> cursorValueOf(dto, request.sortByOrDefault()),
        UserDto::id
    );
  }

  @Transactional
  public UserDto updateProfile(UUID targetUserId, UUID requesterId, UserUpdateRequest request) {
    validateOwner(targetUserId, requesterId);
    User user = findUserOrThrow(targetUserId);

    String imageUrl = null;
    MultipartFile profileImage = request.profileImage();
    if(profileImage != null && !profileImage.isEmpty()) {
      imageUrl = fileStorageService.upload(profileImage);
    }

    user.updateProfile(request.name(), imageUrl);

    return UserDto.from(user);
  }

  @Transactional
  public void changePassword(UUID targetUserId, UUID requesterId, ChangePasswordRequest request) {
    validateOwner(targetUserId, requesterId);
    User user = findUserOrThrow(targetUserId);
    user.changePassword(passwordEncoder.encode(request.password()));
  }

  private User findUserOrThrow(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
  }

  private void validateOwner(UUID targetUserId, UUID requesterId) {
    if(!targetUserId.equals(requesterId)) {
      throw new UserException(UserErrorCode.FORBIDDEN_ACCESS);
    }
  }

  private String cursorValueOf(UserDto dto, UserSortBy sortBy) {
    return switch (sortBy) {
      case name -> dto.name();
      case email -> dto.email();
      case createdAt -> dto.createdAt().toString();
      case isLocked -> String.valueOf(dto.locked());
      case role -> dto.role().name();
    };
  }
}
