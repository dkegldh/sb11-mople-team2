package com.codeit.mople.domain.follow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.follow.dto.FollowRequest;
import com.codeit.mople.domain.follow.dto.FollowResponse;
import com.codeit.mople.domain.follow.repository.FollowRepository;
import com.codeit.mople.domain.follow.service.FollowService;
import com.codeit.mople.domain.notification.service.NotificationCreator;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.config.CacheNames;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
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

  @MockitoSpyBean
  CacheManager cacheManager;

  @MockitoBean
  NotificationCreator notificationCreator;

  @Autowired
  MockMvc mockMvc;

  @Autowired
  PlatformTransactionManager transactionManager;

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
    reset(cacheManager);
    cacheManager.getCache(CacheNames.FOLLOW_COUNT).clear();
  }

  private void breakFollowCountCache() {
    Cache brokenCache = mock(Cache.class);
    given(brokenCache.getName()).willReturn(CacheNames.FOLLOW_COUNT);
    given(brokenCache.get(any())).willThrow(new RedisConnectionFailureException("redis down"));
    willThrow(new RedisConnectionFailureException("redis down"))
        .given(brokenCache).put(any(), any());

    willReturn(brokenCache).given(cacheManager).getCache(CacheNames.FOLLOW_COUNT);
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

  @Test
  @DisplayName("캐시 조회가 실패해도 원본을 조회해 200을 반환하는지")
  void fallsBackToOriginWhenCacheIsDown() throws Exception {
    // given
    breakFollowCountCache();

    // when & then
    mockMvc.perform(get("/api/follows/count")
            .param("followeeId", followeeId.toString())
            .with(user(new CustomUserDetails(followerId, Role.USER))))
        .andExpect(status().isOk())
        .andExpect(content().string("0"));
  }

  @Test
  @DisplayName("바깥 트랜잭션 없이 팔로우하면 메서드가 반환된 시점에 캐시 키가 이미 지워져 있는지")
  void evictsCacheBeforeFollowReturnsWhenCalledOutsideTransaction() {
    // given
    followService.getFollowCount(followeeId);
    assertThat(stringRedisTemplate.hasKey(CACHE_KEY_PREFIX + followeeId)).isTrue();

    // when
    followService.follow(new FollowRequest(followeeId), followerId);

    // then
    assertThat(stringRedisTemplate.hasKey(CACHE_KEY_PREFIX + followeeId)).isFalse();
  }

  @Test
  @DisplayName("바깥 트랜잭션 안에서 조회하면 커밋 전에는 캐시에 안 담기고 커밋 후에 담기는지")
  void defersCachePutUntilOuterTransactionCommits() {
    // given
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

    // when
    transactionTemplate.executeWithoutResult(status -> {
      followService.getFollowCount(followeeId);

      // then
      assertThat(stringRedisTemplate.hasKey(CACHE_KEY_PREFIX + followeeId)).isFalse();
    });

    // then
    assertThat(stringRedisTemplate.hasKey(CACHE_KEY_PREFIX + followeeId)).isTrue();
  }

  @Test
  @DisplayName("캐시가 죽으면 호출마다 원본 집계 쿼리가 나가는지")
  void queriesOriginEveryTimeWhenCacheIsDown() {
    // given
    breakFollowCountCache();

    // when
    followService.getFollowCount(followeeId);
    followService.getFollowCount(followeeId);

    // then
    verify(followRepository, times(2)).countByFolloweeId(followeeId);
  }
}