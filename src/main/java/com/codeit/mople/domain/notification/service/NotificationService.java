package com.codeit.mople.domain.notification.service;

import com.codeit.mople.domain.notification.dto.request.NotificationCursorRequest;
import com.codeit.mople.domain.notification.dto.response.CursorResponseNotificationDto;
import com.codeit.mople.domain.notification.dto.response.NotificationResponse;
import com.codeit.mople.domain.notification.entity.Notification;
import com.codeit.mople.domain.notification.entity.NotificationType;
import com.codeit.mople.domain.notification.exception.NotificationErrorCode;
import com.codeit.mople.domain.notification.exception.NotificationException;
import com.codeit.mople.domain.notification.repository.NotificationRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.exception.UserException;
import com.codeit.mople.domain.user.repository.UserRepository;
import java.time.Instant;
import org.springframework.dao.TransientDataAccessException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public CursorResponseNotificationDto getNotifications(UUID receiverId,
        NotificationCursorRequest request) {
        log.debug("알림 목록 조회 요청 - receiverId: {}, limit: {}, cursor: {}", receiverId,
            request.limit(), request.cursor());

        Instant cursorTime = request.parseCursorToInstant();
        List<Notification> notifications = notificationRepository.findNotificationByCursor(
            receiverId, cursorTime, request.idAfter(), request.limit());

        boolean hasNext = notifications.size() > request.limit();
        List<Notification> sliced = hasNext ? notifications.subList(0, request.limit()) : notifications;

        List<NotificationResponse> data = sliced.stream()
            .map(n -> new NotificationResponse(
                n.getId(),
                n.getCreatedAt(),
                n.getReceiver().getId(),
                n.getTitle(),
                n.getContent(),
                n.getLevel()
            ))
            .toList();

        String nextCursor = null;
        UUID nextIdAfter = null;

        if (hasNext && !sliced.isEmpty()) {
            Notification lastItem = sliced.get(sliced.size() - 1);
            nextCursor = lastItem.getCreatedAt().toString();
            nextIdAfter = lastItem.getId();
        }

        long totalCount = notificationRepository.countByReceiver_Id(receiverId);
        log.info("알림 목록 조회 완료 - receiverId: {}", receiverId);

        return new CursorResponseNotificationDto(
            data,
            nextCursor,
            nextIdAfter,
            hasNext,
            totalCount,
            request.sortBy(),
            request.sortDirection()
        );
    }

    // DataAccessException 전체가 아닌 TransientDataAccessException(일시적 장애 계열)만 재시도 대상으로 삼는다.
    // DataIntegrityViolationException 같은 비일시적 실패는 재시도해도 결과가 같아 async 스레드만 낭비된다.
    @Retryable(retryFor = TransientDataAccessException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    @Transactional
    public void createNotification(UUID receiverId, String title, String content, NotificationType type) {
        log.debug("알림 생성 요청 - receiverId: {}, type: {}", receiverId, type);
        User receiver = userRepository.findById(receiverId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        notificationRepository.save(Notification.create(receiver, title, content, type));
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

    @Transactional
    public void deleteNotification(UUID notificationId, UUID receiverId) {
        log.debug("알림 삭제 요청 - notificationId: {}, receiverId: {}", notificationId, receiverId);
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.getReceiver().getId().equals(receiverId)) {
            throw new NotificationException(NotificationErrorCode.NOTIFICATION_FORBIDDEN);
        }
        notificationRepository.delete(notification);
        log.info("알림 삭제 완료 - notificationId: {}, receiverId: {}", notificationId, receiverId);
    }
}
