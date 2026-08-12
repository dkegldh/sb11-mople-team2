package com.codeit.mople.domain.notification.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.notification.entity.NotificationType;
import com.codeit.mople.domain.notification.repository.NotificationRepository;
import com.codeit.mople.domain.notification.service.NotificationCreator;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.exception.UserException;
import com.codeit.mople.domain.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@DisplayName("NotificationCreator 재시도/복구 통합 테스트")
class NotificationCreatorRetryIntegrationTest {

    @Autowired
    private NotificationCreator notificationCreator;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("존재하지 않는 유저면 재시도 없이 UserException이 흡수되지 않고 전파된다")
    void 존재하지_않는_유저면_재시도_없이_UserException이_흡수되지_않고_전파된다() {
        // given
        UUID receiverId = UUID.randomUUID();
        given(userRepository.findById(receiverId)).willReturn(Optional.empty());

        // when & then
        // TransientDataAccessException 전용 @Recover에 매칭되지 않으므로 Spring Retry가
        // ExhaustedRetryException(cause=UserException)으로 감싸 그대로 전파한다 (조용히 삼켜지지 않음)
        assertThatThrownBy(() -> notificationCreator.createNotification(
            receiverId, "제목", "내용", NotificationType.ROLE_CHANGE))
            .isInstanceOf(ExhaustedRetryException.class)
            .hasCauseInstanceOf(UserException.class);

        // TransientDataAccessException이 아니므로 retryFor 대상이 아니라 재시도 없이 1번만 조회된다
        verify(userRepository, times(1)).findById(receiverId);
        verify(notificationRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("저장이 계속 TransientDataAccessException을 던지면 3회 재시도 후 예외 없이 recover된다")
    void 저장이_계속_TransientDataAccessException을_던지면_3회_재시도_후_예외없이_recover된다() {
        // given
        UUID receiverId = UUID.randomUUID();
        User receiver = User.createUser("receiver@test.com", "encoded", "수신자");
        given(userRepository.findById(receiverId)).willReturn(Optional.of(receiver));
        given(notificationRepository.save(any()))
            .willThrow(new TransientDataAccessResourceException("일시적 DB 커넥션 장애"));

        // when & then - recover가 예외를 흡수하므로 밖으로 전파되지 않는다
        assertThatCode(() -> notificationCreator.createNotification(
            receiverId, "제목", "내용", NotificationType.ACCOUNT_LOCKED))
            .doesNotThrowAnyException();

        // maxAttempts = 3이므로 정확히 3번 재시도된다
        verify(notificationRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("저장이 DataIntegrityViolationException(비일시적)을 던지면 재시도 없이 바로 전파된다")
    void 저장이_비일시적_DataAccessException을_던지면_재시도_없이_바로_전파된다() {
        // given
        UUID receiverId = UUID.randomUUID();
        User receiver = User.createUser("receiver@test.com", "encoded", "수신자");
        given(userRepository.findById(receiverId)).willReturn(Optional.of(receiver));
        given(notificationRepository.save(any()))
            .willThrow(new DataIntegrityViolationException("제약 조건 위반"));

        // when & then - 재시도해도 결과가 같은 예외이므로 재시도 없이 바로 전파되어야 한다
        assertThatThrownBy(() -> notificationCreator.createNotification(
            receiverId, "제목", "내용", NotificationType.ACCOUNT_LOCKED))
            .isInstanceOf(ExhaustedRetryException.class)
            .hasCauseInstanceOf(DataIntegrityViolationException.class);

        // TransientDataAccessException이 아니므로 async 스레드를 낭비하는 재시도 없이 1번만 호출된다
        verify(notificationRepository, times(1)).save(any());
    }
}
