package com.codeit.mople.domain.follow.event;

import com.codeit.mople.global.config.CacheNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class FollowCountCacheEvictListener {

  @CacheEvict(cacheNames = CacheNames.FOLLOW_COUNT, key = "#event.followeeId()")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFollowCreated(FollowCreatedEvent event) {
    log.debug("팔로우 생성으로 팔로워 수 캐시를 무효화합니다: followeeId={}", event.followeeId());
  }

  @CacheEvict(cacheNames = CacheNames.FOLLOW_COUNT, key = "#event.followeeId()")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFollowDeleted(FollowDeletedEvent event) {
    log.debug("팔로우 취소로 팔로워 수 캐시를 무효화합니다: followeeId={}", event.followeeId());
  }
}