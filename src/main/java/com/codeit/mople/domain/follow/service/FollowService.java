package com.codeit.mople.domain.follow.service;

import com.codeit.mople.domain.follow.dto.FollowRequest;
import com.codeit.mople.domain.follow.dto.FollowResponse;
import com.codeit.mople.domain.follow.entity.Follow;
import com.codeit.mople.domain.follow.event.FollowCreatedEvent;
import com.codeit.mople.domain.follow.exception.FollowErrorCode;
import com.codeit.mople.domain.follow.exception.FollowException;
import com.codeit.mople.domain.follow.mapper.FollowMapper;
import com.codeit.mople.domain.follow.repository.FollowRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.error.CustomException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  private final FollowMapper followMapper;
  private final ApplicationEventPublisher publisher;

  @Transactional
  public FollowResponse follow(FollowRequest request, UUID followerId) {

    UUID followeeId = request.followeeId();
    log.debug("팔로우 시도: followeeId={}, followerId={}", followeeId, followerId);

    // 자기 자신 팔로우 안돼
    if (followerId.equals(followeeId)) {
      throw new CustomException(FollowErrorCode.FOLLOW_SELF_NOT_ALLOWED);
    }

    // 이미 팔로가 되어있으면 중복 팔로우 안돼
    if (followRepository.existsByFolloweeIdAndFollowerId(followeeId, followerId)) {
      throw new CustomException(FollowErrorCode.FOLLOW_DUPLICATE);
    }

    // 영속화
    User followee = userRepository.findById(followeeId)
        .orElseThrow(() -> new CustomException(FollowErrorCode.FOLLOWEE_NOT_FOUND));
    User follower = userRepository.findById(followerId)
        .orElseThrow(() -> new CustomException(FollowErrorCode.FOLLOWER_NOT_FOUND));
    Follow saved = followRepository.save(Follow.create(followee, follower));

    log.info("팔로우 성공: followId={}, followeeId={}, followerId={}", saved.getId(), followeeId, followerId);

    // 알림을 위한 이벤트 발행, 발신자 이름 포함했음
    publisher.publishEvent(new FollowCreatedEvent(saved.getId(), followeeId, followerId, follower.getName()));

    // mapper로 리턴
    return followMapper.toFollowResponse(saved);
  }

  @Transactional
  public void unFollow(UUID followId, UUID followerId) {

    log.debug("팔로우 취소 시도: followId={}, followerId={}", followId, followerId);

    // 해당 followId가 있는지 검증
     Follow follow = followRepository.findById(followId)
         .orElseThrow(() -> new CustomException(FollowErrorCode.FOLLOW_NOT_FOUND));

     // 본인의 팔로우만 언팔 가능
     if (!follow.getFollower().getId().equals(followerId)) {
       throw new CustomException(FollowErrorCode.FOLLOW_NOT_OWNER);
     }

     // 해당 팔로우(row) 삭제
     followRepository.delete(follow);
     log.info("팔로우 취소 성공: followId={}, followerId={}", followId, followerId);
  }

  public FollowResponse getFollowByMe(UUID followeeId, UUID followerId) {
    log.debug("팔로우 여부 조회: followeeId={}, followerId={}", followeeId, followerId);
    Follow followByMe = followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId)
        .orElseThrow(() -> new FollowException(FollowErrorCode.FOLLOW_BY_ME_NOT_FOUND));
    return followMapper.toFollowResponse(followByMe);
  }

  public long getFollowCount(UUID followeeId) {
    log.debug("팔로우 수 조회: followeeId={}", followeeId);
    return followRepository.countByFolloweeId(followeeId);
  }
}
