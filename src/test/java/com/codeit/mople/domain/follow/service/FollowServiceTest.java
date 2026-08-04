package com.codeit.mople.domain.follow.service;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.mople.domain.follow.dto.FollowRequest;
import com.codeit.mople.domain.follow.dto.FollowResponse;
import com.codeit.mople.domain.follow.entity.Follow;
import com.codeit.mople.domain.follow.event.FollowCreatedEvent;
import com.codeit.mople.domain.follow.exception.FollowErrorCode;
import com.codeit.mople.domain.follow.repository.FollowRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.error.CustomException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

  @InjectMocks
  FollowService followService;

  @Mock
  FollowRepository followRepository;
  @Mock
  UserRepository userRepository;
  @Mock
  ApplicationEventPublisher publisher;

  @Captor
  ArgumentCaptor<Follow> followCaptor;

  UUID followeeId;
  UUID followerId;
  FollowRequest request;

  @BeforeEach
  void setUp() {
    followeeId = UUID.randomUUID();
    followerId = UUID.randomUUID();
    request = new FollowRequest(followeeId);
  }

  @Nested
  @DisplayName("팔로우 생성")
  class CreateFollow {

    @Test
    @DisplayName("정상 요청이면 팔로우를 저장하고 생성 이벤트를 발행한다")
    void 정상_요청이면_팔로우를_저장하고_생성_이벤트를_발행한다() {
      // given
      User followee = mock(User.class);
      User follower = mock(User.class);
      given(follower.getName()).willReturn("아메리카노좋아");
      Follow saved = Follow.create(followee, follower);
      FollowResponse expected = new FollowResponse(saved.getId(), followeeId, followerId);

      given(followRepository.existsByFolloweeIdAndFollowerId(followeeId, followerId)).willReturn(false);
      given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
      given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
      given(followRepository.save(any(Follow.class))).willReturn(saved);
      given(followee.getId()).willReturn(followeeId);
      given(follower.getId()).willReturn(followerId);


      // when
      FollowResponse actual = followService.follow(request, followerId);

      // then
      assertThat(actual).isEqualTo(expected);

      verify(followRepository).save(followCaptor.capture());
      Follow captured = followCaptor.getValue();
      assertThat(captured.getFollowee()).isSameAs(followee);
      assertThat(captured.getFollower()).isSameAs(follower);

      verify(publisher).publishEvent(
          new FollowCreatedEvent(saved.getId(), followeeId, followerId, "아메리카노좋아"));
    }

    @Test
    @DisplayName("자기 자신을 팔로우하면 예외가 발생한다")
    void 자기_자신을_팔로우하면_예외가_발생한다() {

      // given: 자기 자신과 동일한 ID를 만들어줘야함
      FollowRequest selfRequest = new FollowRequest(followerId);

      // when & then
      assertThatExceptionOfType(CustomException.class)
          .isThrownBy(() -> followService.follow(selfRequest, followerId))
          .extracting(CustomException::getErrorCode)
          .isEqualTo(FollowErrorCode.FOLLOW_SELF_NOT_ALLOWED);

      verifyNoInteractions(userRepository, followRepository, publisher);
    }

    @Test
    @DisplayName("팔로우 대상이 존재하지 않으면 예외가 발생한다")
    void 팔로우_대상이_존재하지_않으면_예외가_발생한다() {

      // given
      given(followRepository.existsByFolloweeIdAndFollowerId(followeeId, followerId)).willReturn(false);
      given(userRepository.findById(followeeId)).willReturn(Optional.empty());

      assertThatExceptionOfType(CustomException.class)
          .isThrownBy(() -> followService.follow(request, followerId))
          .extracting(CustomException::getErrorCode)
          .isEqualTo(FollowErrorCode.FOLLOW_FOLLOWEE_NOT_FOUND);

      verify(followRepository, never()).save(any(Follow.class));
      verify(publisher, never()).publishEvent(any(FollowCreatedEvent.class));
    }

    @Test
    @DisplayName("요청자 정보를 찾을 수 없으면 예외가 발생한다")
    void 요청자_정보를_찾을_수_없으면_예외가_발생한다() {

      // given
      User followee = mock(User.class);
      given(followRepository.existsByFolloweeIdAndFollowerId(followeeId, followerId)).willReturn(false);
      given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
      given(userRepository.findById(followerId)).willReturn(Optional.empty());

      // when & then
      assertThatExceptionOfType(CustomException.class)
          .isThrownBy(() -> followService.follow(request, followerId))
          .extracting(CustomException::getErrorCode)
          .isEqualTo(FollowErrorCode.FOLLOW_FOLLOWER_NOT_FOUND);

      verify(followRepository, never()).save(any(Follow.class));
      verify(publisher, never()).publishEvent(any(FollowCreatedEvent.class));
    }

    @Test
    @DisplayName("이미 팔로우 중이면 예외가 발생하고 중복 저장하지 않는다")
    void 이미_팔로우_중이면_예외가_발생하고_중복_저장하지_않는다() {

      // given
      given(followRepository.existsByFolloweeIdAndFollowerId(followeeId, followerId)).willReturn(true);

      // when & then
      assertThatExceptionOfType(CustomException.class)
          .isThrownBy(() -> followService.follow(request, followerId))
          .extracting(CustomException::getErrorCode)
          .isEqualTo(FollowErrorCode.FOLLOW_DUPLICATE);

      verify(followRepository, never()).save(any(Follow.class));
      verify(publisher, never()).publishEvent(any(FollowCreatedEvent.class));
    }
  }

  @Test
  @DisplayName("팔로우 취소 성공")
  void unFollow_success() {
    UUID followId = UUID.randomUUID();
    User followee = mock(User.class);
    User follower = mock(User.class);
    given(follower.getId()).willReturn(followerId);

    Follow follow = Follow.create(followee, follower);
    given(followRepository.findById(followId)).willReturn(Optional.of(follow));

    // when
    followService.unFollow(followId, followerId);

    // then
    verify(followRepository).delete(follow);
  }

  @Test
  @DisplayName("팔로우 정보가 없으면 예외 발생")
  void unFollow_notFound() {
    // given: 팔로우Id 조회해서 없으면 empty 반환해
    UUID followId = UUID.randomUUID();
    given(followRepository.findById(followId)).willReturn(Optional.empty());

    // when & then
    assertThatExceptionOfType(CustomException.class)
        .isThrownBy(() -> followService.unFollow(followId, followerId))
        .extracting(CustomException::getErrorCode)
        .isEqualTo(FollowErrorCode.UNFOLLOW_NOT_FOUND);

    verify(followRepository, never()).delete(any(Follow.class));
  }

  @Test
  @DisplayName("본인의 팔로우가 아니면 예외 발생")
  void unFollow_notOwner() {
    // given
    UUID followId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();

    User followee = mock(User.class);
    User follower = mock(User.class);
    given(follower.getId()).willReturn(otherUserId);

    Follow follow = Follow.create(followee, follower);
    given(followRepository.findById(followId)).willReturn(Optional.of(follow));

    // when & then
    assertThatExceptionOfType(CustomException.class)
        .isThrownBy(() -> followService.unFollow(followId, followerId))
        .extracting(CustomException::getErrorCode)
        .isEqualTo(FollowErrorCode.UNFOLLOW_NOT_OWNER);

    verify(followRepository, never()).delete(any(Follow.class));
  }

  @Test
  @DisplayName("특정 유저를 내가 팔로우하는지 여부 조회 성공")
  void getFollowByMe_success() {
    User followee = mock(User.class);
    User follower = mock(User.class);
    Follow saved = Follow.create(followee, follower);
    FollowResponse expected = new FollowResponse(saved.getId(), followeeId, followerId);

    given(followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId)).willReturn(Optional.of(saved));
    given(followee.getId()).willReturn(followeeId);
    given(follower.getId()).willReturn(followerId);

    // when
    FollowResponse actual = followService.getFollowByMe(followeeId, followerId);

    // then
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @DisplayName("팔로우 중이 아니면 예외 발생")
  void getFollowByMe_notFound() {
    // given: 팔로우Id 조회해서 없으면 empty 반환해
    given(followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId)).willReturn(Optional.empty());

    // when & then
    assertThatExceptionOfType(CustomException.class)
        .isThrownBy(() -> followService.getFollowByMe(followeeId, followerId))
        .extracting(CustomException::getErrorCode)
        .isEqualTo(FollowErrorCode.FOLLOW_BY_ME_NOT_FOUND);
  }

  @Test
  @DisplayName("특정 유저의 팔로워 수 조회 성공")
  void getFollowCount_success() {
    given(followRepository.countByFolloweeId(followeeId)).willReturn(7L);

    // when
    long result = followService.getFollowCount(followeeId);

    // then
    assertThat(result).isEqualTo(7L);
  }

}
