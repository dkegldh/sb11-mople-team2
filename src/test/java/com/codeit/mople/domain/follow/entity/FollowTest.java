package com.codeit.mople.domain.follow.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.codeit.mople.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Follow 엔티티 테스트")
public class FollowTest {

  User followee;
  User follower;

  @BeforeEach
  void setUp() {
    followee = User.createUser("followee@mople.com", "password", "팔로우대상");
    follower = User.createUser("follower@mople.com", "password", "팔로워");
  }

  @Nested
  @DisplayName("팔로우 생성")
  class Create {

    @Test
    @DisplayName("follow")
    void createSuccess() {

      // when
      Follow follow = Follow.create(followee, follower);

      // then
      assertThat(follow.getFollowee()).isEqualTo(followee);
      assertThat(follow.getFollower()).isEqualTo(follower);
    }

    @Test
    @DisplayName("followee가 null이면 NPE가 발생하는지")
    void createThrowsExceptionWhenFolloweeIsNull() {
      // when, then
      assertThatNullPointerException()
          .isThrownBy(() -> Follow.create(null, follower))
          .withMessage("followee");
    }

    @Test
    @DisplayName("follower가 null이면 NPE가 발생하는지")
    void createThrowsExceptionWhenFollowerIsNull() {
      // when, then
      assertThatNullPointerException()
          .isThrownBy(() -> Follow.create(followee, null))
          .withMessage("follower");
    }
  }
}
