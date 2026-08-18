package com.codeit.mople.domain.follow.service;

import com.codeit.mople.domain.follow.dto.FollowRequest;
import com.codeit.mople.domain.follow.dto.FollowResponse;
import com.codeit.mople.domain.follow.entity.Follow;
import com.codeit.mople.domain.follow.event.FollowCreatedEvent;
import com.codeit.mople.domain.follow.exception.FollowErrorCode;
import com.codeit.mople.domain.follow.exception.FollowException;
import com.codeit.mople.domain.follow.repository.FollowRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.config.CacheNames;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

  private final UserRepository userRepository;
  private final FollowRepository followRepository;
  private final ApplicationEventPublisher publisher;

  // CacheEvict -> 팔로우 성공처리되면 이전 데이터에 대한 캐시를 삭제하기 위한 어노테이션(삭제할 캐시 이름, 삭제할 데이터)
  @CacheEvict(cacheNames = CacheNames.FOLLOW_COUNT, key = "#request.followeeId")
  @Transactional
  public FollowResponse follow(FollowRequest request, UUID followerId) {

    UUID followeeId = request.followeeId();
    log.debug("팔로우 시도: followeeId={}, followerId={}", followeeId, followerId);

    // 자기 자신 팔로우 안돼
    if (followerId.equals(followeeId)) {
      throw new FollowException(FollowErrorCode.FOLLOW_SELF_NOT_ALLOWED, Map.of("followeeId", followeeId));
    }

    // 이미 팔로가 되어있으면 중복 팔로우 안돼
    if (followRepository.existsByFolloweeIdAndFollowerId(followeeId, followerId)) {
      throw new FollowException(FollowErrorCode.FOLLOW_DUPLICATE, Map.of("followeeId", followeeId, "followerId", followerId));
    }

    //
    User followee = userRepository.findById(followeeId)
        .orElseThrow(() -> new FollowException(FollowErrorCode.FOLLOW_FOLLOWEE_NOT_FOUND, Map.of("followeeId", followeeId)));
    User follower = userRepository.findById(followerId)
        .orElseThrow(() -> new FollowException(FollowErrorCode.FOLLOW_FOLLOWER_NOT_FOUND, Map.of("followerId", followerId)));
    Follow saved = followRepository.save(Follow.create(followee, follower));

    log.info("팔로우 성공: followId={}, followeeId={}, followerId={}", saved.getId(), followeeId, followerId);

    // 알림을 위한 이벤트 발행, 발신자 이름 포함했음
    publisher.publishEvent(new FollowCreatedEvent(saved.getId(), followeeId, followerId, follower.getName()));

    return FollowResponse.from(saved);
  }

  // CacheEvict -> 여기서 key값이 왜 followeeId가 아니고 result인지?
  // -> followeeId 파라미터가 리턴값에 있음 그러므로 result가 되는것임
  @CacheEvict(cacheNames = CacheNames.FOLLOW_COUNT, key = "#result")
  @Transactional
  public UUID unFollow(UUID followId, UUID followerId) {

    log.debug("팔로우 취소 시도: followId={}, followerId={}", followId, followerId);

    // 해당 followId가 있는지 검증
     Follow follow = followRepository.findById(followId)
         .orElseThrow(() -> new FollowException(FollowErrorCode.UNFOLLOW_NOT_FOUND, Map.of("followId", followId)));

     // 본인의 팔로우만 언팔 가능
     if (!follow.getFollower().getId().equals(followerId)) {
       throw new FollowException(FollowErrorCode.UNFOLLOW_NOT_OWNER, Map.of("followerId", followerId));
     }

     // 삭제 전에 캐시 키를 확보
     UUID followeeId = follow.getFollowee().getId();

     // 해당 팔로우(row) 삭제
     followRepository.delete(follow);
     log.info("팔로우 취소 성공: followId={}, followerId={}", followId, followerId);

     return followeeId;
  }

  public FollowResponse getFollowByMe(UUID followeeId, UUID followerId) {
    log.debug("팔로우 여부 조회: followeeId={}, followerId={}", followeeId, followerId);
    Follow followByMe = followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId)
        .orElseThrow(() -> new FollowException(FollowErrorCode.FOLLOW_BY_ME_NOT_FOUND, Map.of("followeeId", followeeId, "followerId", followerId)));
    return FollowResponse.from(followByMe);
  }

  // Cacheable -> 캐시에 해당 데이터가 있으면 메서드를 실행하지 않고 캐시에서 마로 꺼냄,
  // 없으면 메서드를 실행한 뒤 그 결과를 캐시에 저장(네임스페이스 지정, 키, 값=반환된값)
  @Cacheable(cacheNames = CacheNames.FOLLOW_COUNT, key = "#followeeId")
  public long getFollowCount(UUID followeeId) {
    log.debug("팔로우 수 조회: followeeId={}", followeeId);
    return followRepository.countByFolloweeId(followeeId);
  }

  public List<UUID> getFollowerIds(UUID followeeId) {
    log.debug("팔로워 id 목록 조회: followeeId={}", followeeId);
    return followRepository.findFollowerIdsByFolloweeId(followeeId);
  }
}
