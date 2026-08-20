package com.codeit.mople.domain.follow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.follow.dto.FollowRequest;
import com.codeit.mople.domain.follow.dto.FollowResponse;
import com.codeit.mople.domain.follow.repository.FollowRepository;
import com.codeit.mople.domain.follow.service.FollowService;
import com.codeit.mople.domain.notification.service.NotificationCreator;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.config.CacheNames;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("팔로워 수 캐시 통합 테스트")
class FollowCountCacheTest {

  private static final String CACHE_KEY_PREFIX = "mople:test:cache:" + CacheNames.FOLLOW_COUNT + "::";

  @Autowired
  FollowService followService;

  @Autowired
  UserRepository userRepository;
  
  @MockitoSpyBean
  FollowRepository followRepository;

  @Autowired
  StringRedisTemplate stringRedisTemplate;

  @Autowired
  CacheManager cacheManager;
  
  @MockitoBean
  NotificationCreator notificationCreator;

  UUID followeeId;
  UUID followerId;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString();
    followeeId = userRepository.save(
        User.createUser("followee-" + suffix + "@mople.com", "password", "팔로우대상")).getId();
    followerId = userRepository.save(
        User.createUser("follower-" + suffix + "@mople.com", "password", "팔로워")).getId();

    clearFollowCountCache();
  }

  @AfterEach
  void tearDown() {
    followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId)
        .ifPresent(followRepository::delete);
    userRepository.deleteById(followeeId);
    userRepository.deleteById(followerId);

    clearFollowCountCache();
  }

  private void clearFollowCountCache() {
    cacheManager.getCache(CacheNames.FOLLOW_COUNT).clear();
  }

  @Test
  @DisplayName("두 번 조회하면 집계 쿼리는 한 번만 나가는지")
  void cachesFollowerCount() {
    // when
    followService.getFollowCount(followeeId);
    followService.getFollowCount(followeeId);
    
    verify(followRepository, times(1)).countByFolloweeId(followeeId);
  }

  @Test
  @DisplayName("팔로우하면 캐시가 무효화되어 다음 조회가 새 값을 반환하는지")
  void evictsCacheAfterFollow() {
    // given
    assertThat(followService.getFollowCount(followeeId)).isZero();

    // when
    followService.follow(new FollowRequest(followeeId), followerId);

    // then
    assertThat(followService.getFollowCount(followeeId)).isEqualTo(1L);
  }

  @Test
  @DisplayName("팔로우 취소하면 캐시가 무효화되어 다음 조회가 새 값을 반환하는지")
  void evictsCacheAfterUnfollow() {
    // given
    FollowResponse created = followService.follow(new FollowRequest(followeeId), followerId);
    assertThat(followService.getFollowCount(followeeId)).isEqualTo(1L);

    // when
    followService.unFollow(created.id(), followerId);

    // then
    assertThat(followService.getFollowCount(followeeId)).isZero();
  }

  @Test
  @DisplayName("캐시 키가 환경 네임스페이스 아래에 만들어지는지")
  void usesNamespacedCacheKey() {
    // when
    followService.getFollowCount(followeeId);

    // then
    assertThat(stringRedisTemplate.hasKey(CACHE_KEY_PREFIX + followeeId)).isTrue();
  }
}