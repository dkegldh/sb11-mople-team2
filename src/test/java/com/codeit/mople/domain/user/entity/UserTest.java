package com.codeit.mople.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserTest {

  @Test
  @DisplayName("User 생성 시 기본값은 USER 권한, 잠금 해제 상태")
  void createUser_defaultValues() {
    User user = User.createUser("test@test.com", "encodedPassword", "testUser");

    assertThat(user.getEmail()).isEqualTo("test@test.com");
    assertThat(user.getRole()).isEqualTo(Role.USER);
    assertThat(user.isLocked()).isFalse();
  }

  @Test
  @DisplayName("email에 대문자가 섞여 있어도 소문자로 정규화되어 저장됨")
  void createUser_normalizesEmailToLowerCase() {
    User user = User.createUser("TestUser@Test.com", "encodedPassword", "testUser");

    assertThat(user.getEmail()).isEqualTo("testuser@test.com");
  }

  @Test
  @DisplayName("email이 null이면 User 생성 시 예외가 발생함")
  void createUser_throwsException_whenEmailIsNull() {
    assertThatThrownBy(() -> User.createUser(null, "encodedPassword", "testUser"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("email");
  }

  @Test
  @DisplayName("password가 null이면 User 생성 시 예외가 발생함")
  void createUser_throwsException_whenPasswordIsNull() {
    assertThatThrownBy(() -> User.createUser("test@test.com",  null, "testUser"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("password");
  }

  @Test
  @DisplayName("name이 null이면 User 생성 시 예외가 발생함")
  void createUser_throwsException_whenNameIsNull() {
    assertThatThrownBy(() -> User.createUser("test@test.com", "encodedPassword", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name");
  }

  @Test
  @DisplayName("프로필 변경 시 이름과 프로필 사진이 갱신됨")
  void updateProfile_success() {
    User user = User.createUser("test@test.com", "encodedPassword", "pastName");

    user.updateProfile("newName", "https://new-image.url");

    assertThat(user.getName()).isEqualTo("newName");
    assertThat(user.getProfileImageUrl()).isEqualTo("https://new-image.url");
  }

  @Test
  @DisplayName("이름만 전달하면 이름만 갱신되고 기존 프로필 이미지는 유지됨")
  void updateProfile_success_nameOnly() {
    User user = User.createUser("test@test.com", "encodedPassword", "pastName");
    user.updateProfile(null, "https://existing-image.url");

    user.updateProfile("newName", null);

    assertThat(user.getName()).isEqualTo("newName");
    assertThat(user.getProfileImageUrl()).isEqualTo("https://existing-image.url");
  }

  @Test
  @DisplayName("이미지만 전달하면 이미지만 갱신됨")
  void updateProfile_success_imageOnly() {
    User user = User.createUser("test@test.com", "encodedPassword", "pastName");

    user.updateProfile(null, "https://new-image.url");

    assertThat(user.getName()).isEqualTo("pastName");
    assertThat(user.getProfileImageUrl()).isEqualTo("https://new-image.url");
  }

  @Test
  @DisplayName("이름과 이미지 모두 null이면 기존 값이 그대로 유지됨")
  void updateProfile_noChange_whenBothNull() {
    User user = User.createUser("test@test.com", "encodedPassword", "pastName");
    user.updateProfile(null, "https://existing-image.url");

    user.updateProfile(null, null);

    assertThat(user.getName()).isEqualTo("pastName");
    assertThat(user.getProfileImageUrl()).isEqualTo("https://existing-image.url");
  }

  @Test
  @DisplayName("changePassword에 null을 전달하면 예외가 발생함")
  void changePassword_throwsException_whenNewPasswordIsNull() {
    User user = User.createUser("test@test.com",  "encodedPassword", "testUser");

    assertThatThrownBy(() -> user.changePassword(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("encodedNewPassword");
  }

  @Test
  @DisplayName("changeRole에 null을 전달하면 예외가 발생함")
  void changeRole_throwsException_whenRoleIsNull() {
    User user = User.createUser("test@test.com", "encodedPassword", "testUser");

    assertThatThrownBy(() -> user.changeRole(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("role");
  }

  @Test
  @DisplayName("increaseSessionVersion 호출 시 버전이 1 증가한다.")
  void increaseSessionVersion_success() {
    User user = User.createUser("test@test.com", "encoded", "testUser");
    assertThat(user.increaseSessionVersion()).isEqualTo(1L);
    assertThat(user.increaseSessionVersion()).isEqualTo(2L);
  }

  @Test
  @DisplayName("createOAuthUser로 생성 시 비밀번호 없이 지정한 provider, providerId, 프로필 이미지로 생성됨")
  void createOAuthUser_success() {
    User user = User.createOAuthUser("oauth@test.com", "oauthUser", "https://profile.image", AuthProvider.GOOGLE, "google-sub-123");

    assertThat(user.getEmail()).isEqualTo("oauth@test.com");
    assertThat(user.getName()).isEqualTo("oauthUser");
    assertThat(user.getProfileImageUrl()).isEqualTo("https://profile.image");
    assertThat(user.getProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(user.getProviderId()).isEqualTo("google-sub-123");
    assertThat(user.getPassword()).isNull();
    assertThat(user.getRole()).isEqualTo(Role.USER);
  }

  @Test
  @DisplayName("createOAuthUser에 provider가 null이면 예외가 발생함")
  void createOAuthUser_throwsException_whenProviderIsNull() {
    assertThatThrownBy(() -> User.createOAuthUser("oauth@test.com", "oasuthUser", null, null, "google-sub-123"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("provider");
  }

  @Test
  @DisplayName("createOAuthUser에 providerId가 null이면 예외가 발생함")
  void createOAuthUser_throwsException_whenProviderIdIsNull() {
    assertThatThrownBy(() -> User.createOAuthUser("oauth@test.com", "oasuthUser", null, AuthProvider.GOOGLE, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("providerId");
  }

  @Test
  @DisplayName("createUser/createAdmin로 생성한 계정은 provider가 LOCAL로 설정됨")
  void createUser_and_createAdmin_haveLocalProvider() {
    User user = User.createUser("test@test.com", "encodedPassword", "testUser");
    User admin = User.createAdmin("admin@test.com", "encodedPassword", "adminUser");

    assertThat(user.getProvider()).isEqualTo(AuthProvider.LOCAL);
    assertThat(admin.getProvider()).isEqualTo(AuthProvider.LOCAL);
  }
}
