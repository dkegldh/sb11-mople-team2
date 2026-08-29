package com.codeit.mople.domain.notification.service;

import com.codeit.mople.domain.notification.entity.Notification;
import com.codeit.mople.domain.notification.entity.NotificationType;
import com.codeit.mople.domain.notification.event.NotificationCreatedEvent;
import com.codeit.mople.domain.notification.repository.NotificationRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.exception.UserException;
import com.codeit.mople.domain.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 알림 생성은 여러 도메인 이벤트에 반응해 비동기로 호출되는 쓰기 전용 책임이라, 조회 위주라
// 클래스 레벨이 readOnly인 NotificationService와 분리했다. 재시도 정책(@Retryable/@Recover)도
// 이 클래스에만 걸린다.
//
// 전달 보장 정책: 알림 생성은 best-effort다. TransientDataAccessException 재시도(최대 3회)가
// 소진되거나, 예외가 애초에 재시도 대상이 아니거나, executor 큐가 가득 차 제출 자체가
// 거부되거나, 배포로 인스턴스가 내려가면서 큐에 남아있던 작업이 실행되지 못하면 알림은
// 유실될 수 있고 이를 저장해 재처리하는 별도 경로(outbox 등)는 없다. 강제 로그아웃처럼
// 사용자가 반드시 알아야 하는 안내조차 이 한계 안에 있다는 점을 감안해야 한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCreator {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    private final ApplicationEventPublisher eventPublisher;

    // DataAccessException 전체가 아닌 TransientDataAccessException(일시적 장애 계열)만 재시도 대상으로 삼는다.
    // DataIntegrityViolationException 같은 비일시적 실패는 재시도해도 결과가 같아 async 스레드만 낭비된다.
    @Retryable(retryFor = TransientDataAccessException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    @Transactional
    public void createNotification(UUID receiverId, String title, String content, NotificationType type) {
        log.debug("알림 생성 요청 - receiverId: {}, type: {}", receiverId, type);
        User receiver = userRepository.findById(receiverId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        Notification notification =
            notificationRepository.save(Notification.create(receiver, title, content, type));

        eventPublisher.publishEvent(new NotificationCreatedEvent(UUID.randomUUID(), notification.getCreatedAt(), receiverId, notification.getId()));
        log.info("알림 생성 완료 - receiverId: {}, type: {}", receiverId, type);
    }

    // @Recover는 retryFor와 무관하게 파라미터 타입으로 매칭되므로, 범위를 retryFor와 동일한
    // TransientDataAccessException으로 좁혀 UserException, DataIntegrityViolationException 등
    // 재시도 대상이 아닌 예외까지 흡수되지 않도록 한다.
    // 매칭되는 recover가 없으면 Spring Retry가 ExhaustedRetryException(cause=원본 예외)으로 감싸
    // 호출자(비동기 리스너)에 전파하고, 최종적으로 AsyncConfig.getAsyncUncaughtExceptionHandler 로그로 남는다.
    @Recover
    public void recoverCreateNotification(TransientDataAccessException e, UUID receiverId, String title, String content, NotificationType type) {
        log.error("알림 생성 최종 실패 (3회 재시도 소진) - receiverId: {}, type: {}", receiverId, type, e);
    }
}
