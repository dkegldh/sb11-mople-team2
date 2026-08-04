package com.codeit.mople.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.codeit.mople.global.dto.SortDirection;
import com.codeit.mople.global.error.CustomException;
import com.codeit.mople.global.storage.FileStorageService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private FileStorageService fileStorageService;

  @InjectMocks
  private UserService userService;

  private User user;

  @BeforeEach
  void setUp() {
    user = User.createUser("test@test.com", "encodedPassword", "testUser");
  }

  @Test
  @DisplayName("회원가입 성공")
  void signUp_success() {
    UserCreateRequest request = new UserCreateRequest("test@test.com", "rawPw123", "testUser");
    when(userRepository.existsByEmail(request.email())).thenReturn(false);
    when(passwordEncoder.encode(request.password())).thenReturn("encodedPw");
    when(userRepository.save(any(User.class))).thenReturn(user);

    UserDto response = userService.signUp(request);

    assertThat(response.email()).isEqualTo("test@test.com");
    verify(userRepository).save(any(User.class));
  }

  @Test
  @DisplayName("이메일 중복 시 예외 발생 및 중복된 이메일 정보 포함")
  void signUp_throwsException_whenEmailDuplicated() {
    UserCreateRequest request = new UserCreateRequest("dup@test.com", "rawPw123", "testUser");
    when(userRepository.existsByEmail(request.email())).thenReturn(true);

    assertThatThrownBy(() -> userService.signUp(request))
        .isInstanceOf(UserException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.DUPLICATE_EMAIL)
        .satisfies(e -> {
          UserException ue = (UserException) e;
          assertThat(ue.getDetails()).containsEntry("email", "dup@test.com");
        });
  }

  @Test
  @DisplayName("사용자 조회 성공")
  void getUser_success() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    UserDto response = userService.getUser(userId);

    assertThat(response.email()).isEqualTo(user.getEmail());
  }

  @Test
  @DisplayName("존재하지 않는 사용자 조회 시 예외 발생")
  void getUser_throwsException_whenUserNotFound() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getUser(userId))
        .isInstanceOf(UserException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("사용자 목록 조회 시 필터 조건 없이도 정상 동작함")
  void getUsers_success_withoutFilters() {
    UserSearchRequest request = new UserSearchRequest(
        null, null, null, null, null, 10,
        SortDirection.ASCENDING, UserSortBy.name
    );
    User user1 = User.createUser("user1@test.com", "encoded", "user1");
    when(userRepository.searchUsers(request)).thenReturn(List.of(user1));

    CursorResponse<UserDto> response = userService.getUsers(request);

    assertThat(response.data()).hasSize(1);
    verify(userRepository).searchUsers(request);
  }

  @Test
  @DisplayName("사용자 목록 조회 시 hasNext와 nextCursor가 올바르게 설정됨")
  void getUsers_returnsCorrectCursorResponse() {
    UserSearchRequest request = new UserSearchRequest(
        null, null, null, null, null, 2,
        SortDirection.ASCENDING, UserSortBy.name
    );

    User user1 = User.createUser("a@test.com", "encoded", "aa");
    User user2 = User.createUser("b@test.com", "encoded", "bb");
    User user3 = User.createUser("c@test.com", "encoded", "cc");
    when(userRepository.searchUsers(request)).thenReturn(List.of(user1, user2, user3));

    CursorResponse<UserDto> response = userService.getUsers(request);

    assertThat(response.data()).hasSize(2);
    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextCursor()).isEqualTo("bb");
    assertThat(response.sortBy()).isEqualTo("name");
  }

  @Test
  @DisplayName("다음 페이지가 있으면 hasNext가 true이고 nextCursor/nextIdAfter가 채워짐")
  void getUsers_returnsHasNextTrue_whenMoreItemsExist() {
    UserSearchRequest request = new UserSearchRequest(
        null, null, null, null, null, 2,
        SortDirection.ASCENDING, UserSortBy.name
    );
    User user1 = User.createUser("user1@test.com", "encoded", "user1");
    User user2 = User.createUser("user2@test.com", "encoded", "user2");
    User user3 = User.createUser("user3@test.com", "encoded", "user3");
    when(userRepository.searchUsers(request)).thenReturn(List.of(user1, user2, user3));

    CursorResponse<UserDto> response = userService.getUsers(request);

    assertThat(response.data()).hasSize(2);
    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextCursor()).isEqualTo("user2");
    assertThat(response.nextIdAfter()).isEqualTo(user2.getId());
  }

  @Test
  @DisplayName("마지막 페이지면 hasNext가 false이고 nextCursor는 null임")
  void getUsers_returnsHasNextFalse_whenLastPage() {
    UserSearchRequest request = new UserSearchRequest(
        null, null, null, null, null, 10,
        SortDirection.ASCENDING, UserSortBy.name
    );
    User user1 = User.createUser("user1@test.com", "encoded", "user1");
    when(userRepository.searchUsers(request)).thenReturn(List.of(user1));

    CursorResponse<UserDto> response = userService.getUsers(request);

    assertThat(response.hasNext()).isFalse();
    assertThat(response.nextCursor()).isNull();
    assertThat(response.nextIdAfter()).isNull();
  }

  @Test
  @DisplayName("sortBy가 email이면 nextCursor에 email 값이 사용됨")
  void getUsers_usesEmailAsCursor_whenSortByEmail() {
    UserSearchRequest request = new UserSearchRequest(
        null, null, null, null, null, 1,
        SortDirection.ASCENDING, UserSortBy.email
    );
    User user1 = User.createUser("user1@test.com", "encoded", "user1");
    User user2 = User.createUser("user2@test.com", "encoded", "user2");
    when(userRepository.searchUsers(request)).thenReturn(List.of(user1, user2));

    CursorResponse<UserDto> response = userService.getUsers(request);

    assertThat(response.nextCursor()).isEqualTo("user1@test.com");
  }

  @Test
  @DisplayName("본인이 아닌 사용자가 프로필을 수정 시 예외 발생")
  void updateProfile_throwsException_whenNotOwner() {
    UUID userId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();

    UserUpdateRequest request = new UserUpdateRequest("newName", null);

    assertThatThrownBy(() -> userService.updateProfile(userId, otherUserId, request))
        .isInstanceOf(UserException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.FORBIDDEN_ACCESS);
  }

  @Test
  @DisplayName("이름만 변경")
  void updateProfile_success_nameOnly() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    UserUpdateRequest request = new UserUpdateRequest("newName", null);

    UserDto response = userService.updateProfile(userId, userId, request);

    assertThat(response.name()).isEqualTo("newName");
  }

  @Test
  @DisplayName("이미지만 변경")
  void updateProfile_success_imageOnly() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(fileStorageService.upload(any())).thenReturn("https://placeholder.mople.com/test.jpg");

    MockMultipartFile image = new MockMultipartFile("profileImage", "test.jpg", "image/jpeg", "content".getBytes());
    UserUpdateRequest request = new UserUpdateRequest(null, image);

    UserDto response = userService.updateProfile(userId, userId, request);

    assertThat(response.profileImageUrl()).isEqualTo("https://placeholder.mople.com/test.jpg");
    assertThat(response.name()).isEqualTo(user.getName());
  }

  @Test
  @DisplayName("이름과 이미지 둘 다 변경")
  void updateProfile_success_both() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(fileStorageService.upload(any())).thenReturn("https://placeholder.mople.com/test.jpg");

    MockMultipartFile image = new MockMultipartFile("profileImage", "test.jpg", "image/jpeg", "content".getBytes());
    UserUpdateRequest request = new UserUpdateRequest("newName", image);

    UserDto response = userService.updateProfile(userId, userId, request);

    assertThat(response.name()).isEqualTo("newName");
    assertThat(response.profileImageUrl()).isEqualTo("https://placeholder.mople.com/test.jpg");
  }

  @Test
  @DisplayName("비밀번호 변경 성공 시 기존 Refresh Token이 무효화되고 sessionVersion이 증가함")
  void changePassword_success() {
    UUID userId = UUID.randomUUID();
    user.updateRefreshToken("old-refresh-token", Instant.now().plus(7, ChronoUnit.DAYS));
    long beforeSessionVersion = user.getSessionVersion();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.encode("newPw123")).thenReturn("encodedNewPw");

    ChangePasswordRequest request = new ChangePasswordRequest("newPw123");

    userService.changePassword(userId, userId, request);

    assertThat(user.getPassword()).isEqualTo("encodedNewPw");
    assertThat(user.isRefreshTokenValid("old-refresh-token", Instant.now())).isFalse();
    assertThat(user.getSessionVersion()).isEqualTo(beforeSessionVersion + 1);
  }

  @Test
  @DisplayName("본인이 아닌 사용자가 비밀번호 변경 시 예외 발생")
  void changePassword_throwsException_whenNotOwner() {
    UUID userId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();

    ChangePasswordRequest request = new ChangePasswordRequest("newPw123");

    assertThatThrownBy(() -> userService.changePassword(userId, otherUserId, request))
        .isInstanceOf(UserException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.FORBIDDEN_ACCESS);
  }
}
