package com.codeit.mople.domain.follow.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.mople.domain.follow.entity.Follow;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.config.JpaAuditingConfig;
import com.codeit.mople.global.config.QueryDslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@DisplayName("FollowRepository 테스트")
class FollowRepositoryTest {

  @Autowired
  FollowRepository followRepository;

  @Autowired
  TestEntityManager entityManager;

  User followee;
  User follower;
  User other;

  @BeforeEach
  void setUp() {
    followee = entityManager.persist(User.createUser("followee@mopl.com", "password", "팔로워대상"));
    follower = entityManager.persist(User.createUser("follower@mopl.com", "password", "팔로워"));
    other = entityManager.persist(User.createUser("other@mopl.com", "password", "제3자"));
  }

  private void 팔로우_상태로_만들기(User followee, User follower) {
    entityManager.persist(Follow.create(followee, follower));
    entityManager.flush();
    entityManager.clear();
  }

  @Nested
  @DisplayName("중복 확인 [existsByFolloweeIdAndFollowerId]")
  class ExistsByFolloweeIdAndFollowerId {

    @Test
    @DisplayName("이미 팔로우 중이면 true 반환")
    void 이미_팔로우_중이면_true_반환() {
      // given
      팔로우_상태로_만들기(followee, follower);

      // when
      boolean exists =
          followRepository.existsByFolloweeIdAndFollowerId(followee.getId(), follower.getId());

      // then
      assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("같은 사람이 다른 사용자를 팔로우했는지 조회하면 false 반환")
    void 다른_대상_조회하면_false_반환() {
      // given
      팔로우_상태로_만들기(followee, follower);

      // when 같은 follower가 other을 팔로우했는지 조회
      boolean exists =
          followRepository.existsByFolloweeIdAndFollowerId(other.getId(), follower.getId());

      // then
      assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("다른 사람이 같은 대상을 팔로우했는지 조회하면 false 반환")
    void 다른_주체로_조회하면_false_반환() {
      // given
      팔로우_상태로_만들기(followee, follower);

      // when
      boolean exists =
          followRepository.existsByFolloweeIdAndFollowerId(followee.getId(), other.getId());

      // then
      assertThat(exists).isFalse();
    }
  }

  @Nested
  @DisplayName("팔로우 저장 [save]")
  class Save {

    @Test
    @DisplayName("저장하면 followee,follower가 조회된다")
    void 저장되면_조회_가능() {
      // given
      Follow follow = Follow.create(followee, follower);

      // when
      Follow saved = followRepository.save(follow);
      entityManager.flush();
      entityManager.clear();

      // then
      Follow found = entityManager.find(Follow.class, saved.getId());
      assertThat(found).isNotNull();
      assertThat(found.getFollowee().getId()).isEqualTo(followee.getId());
      assertThat(found.getFollower().getId()).isEqualTo(follower.getId());
      assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 (followee, follower) 조합을 두 번 저장 불가 unique 제약에 걸림")
    void 중복_팔로우는_저장_불가() {
      // given
      팔로우_상태로_만들기(followee, follower);
      Follow duplicate = Follow.create(
          entityManager.find(User.class, followee.getId()),
          entityManager.find(User.class, follower.getId()));

      // when & then
      assertThatThrownBy(() -> followRepository.saveAndFlush(duplicate))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }
}
