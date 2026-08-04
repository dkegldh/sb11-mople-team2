package com.codeit.mople.domain.notification.service;

import com.codeit.mople.domain.notification.dto.request.NotificationCursorRequest;
import com.codeit.mople.domain.notification.dto.response.CursorResponseNotificationDto;
import com.codeit.mople.domain.notification.dto.response.NotificationResponse;
import com.codeit.mople.domain.notification.entity.Notification;
import com.codeit.mople.domain.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

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
}
