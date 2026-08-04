package com.codeit.mople.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.codeit.mople.domain.notification.dto.request.NotificationCursorRequest;
import com.codeit.mople.domain.notification.exception.NotificationException;
import com.codeit.mople.domain.notification.dto.response.CursorResponseNotificationDto;
import com.codeit.mople.domain.notification.entity.Notification;
import com.codeit.mople.domain.notification.NotificationLevel;
import com.codeit.mople.domain.notification.repository.NotificationRepository;
import com.codeit.mople.domain.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 테스트")
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    private UUID receiverId;

    @BeforeEach
    void setUp() {
        receiverId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("getNotifications (알림 목록 커서 조회) 테스트")
    class GetNotifications {

        @Test
        @DisplayName("성공: 첫 페이지 조회 시 알림 목록과 응답 메타데이터가 올바르게 반환된다.")
        void success_first_page_returns_correct_data_and_metadata() {
            // given
            NotificationCursorRequest request = new NotificationCursorRequest(
                null, null, 20, "DESCENDING", "createdAt");

            Instant createdAt1 = Instant.parse("2025-08-03T10:00:00Z");
            Instant createdAt2 = Instant.parse("2025-08-02T10:00:00Z");
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            Notification n1 = mockNotification(id1, "팔로우 알림", "홍길동님이 팔로우했습니다.", NotificationLevel.INFO, createdAt1);
            Notification n2 = mockNotification(id2, "역할 변경", "역할이 변경됐습니다.", NotificationLevel.WARNING, createdAt2);

            given(notificationRepository.findNotificationByCursor(eq(receiverId), isNull(), isNull(), eq(20)))
                .willReturn(List.of(n1, n2));
            given(notificationRepository.countByReceiver_Id(receiverId)).willReturn(2L);

            // when
            CursorResponseNotificationDto result = notificationService.getNotifications(receiverId, request);

            // then - 응답 메타데이터 검증
            assertThat(result.hasNext()).isFalse();
            assertThat(result.nextCursor()).isNull();
            assertThat(result.nextIdAfter()).isNull();
            assertThat(result.totalCount()).isEqualTo(2);
            assertThat(result.sortBy()).isEqualTo("createdAt");
            assertThat(result.sortDirection()).isEqualTo("DESCENDING");

            // then - data 크기 검증
            assertThat(result.data()).hasSize(2);

            // then - 첫 번째 알림 필드 검증
            assertThat(result.data().get(0).id()).isEqualTo(id1);
            assertThat(result.data().get(0).title()).isEqualTo("팔로우 알림");
            assertThat(result.data().get(0).content()).isEqualTo("홍길동님이 팔로우했습니다.");
            assertThat(result.data().get(0).level()).isEqualTo(NotificationLevel.INFO);
            assertThat(result.data().get(0).createdAt()).isEqualTo(createdAt1);
            assertThat(result.data().get(0).receiverId()).isEqualTo(receiverId);

            // then - 두 번째 알림 필드 검증
            assertThat(result.data().get(1).id()).isEqualTo(id2);
            assertThat(result.data().get(1).title()).isEqualTo("역할 변경");
            assertThat(result.data().get(1).level()).isEqualTo(NotificationLevel.WARNING);
        }

        @Test
        @DisplayName("성공: 조회 결과가 limit+1개이면 hasNext=true이고 nextCursor는 마지막 항목 기준으로 설정된다.")
        void success_has_next_when_result_exceeds_limit() {
            // given
            NotificationCursorRequest request = new NotificationCursorRequest(
                null, null, 2, "DESCENDING", "createdAt");

            Instant lastItemTime = Instant.parse("2025-08-01T10:00:00Z");
            UUID lastItemId = UUID.randomUUID();

            Notification n1 = mockNotification(UUID.randomUUID(), "알림1", "내용1", NotificationLevel.INFO, Instant.now());
            Notification n2 = mockNotification(lastItemId, "알림2", "내용2", NotificationLevel.INFO, lastItemTime);
            Notification n3 = mock(Notification.class); // limit+1번째 — 슬라이싱으로 제외되어 필드 접근 없음

            given(notificationRepository.findNotificationByCursor(eq(receiverId), isNull(), isNull(), eq(2)))
                .willReturn(List.of(n1, n2, n3));
            given(notificationRepository.countByReceiver_Id(receiverId)).willReturn(3L);

            // when
            CursorResponseNotificationDto result = notificationService.getNotifications(receiverId, request);

            // then
            assertThat(result.data()).hasSize(2);
            assertThat(result.hasNext()).isTrue();
            assertThat(result.nextCursor()).isEqualTo(lastItemTime.toString());
            assertThat(result.nextIdAfter()).isEqualTo(lastItemId);
            // totalCount는 현재 페이지 크기가 아닌 전체 알림 개수
            assertThat(result.totalCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("성공: 조회 결과가 정확히 limit개이면 hasNext=false이고 nextCursor는 null이다.")
        void success_has_next_false_when_result_equals_limit() {
            // given
            NotificationCursorRequest request = new NotificationCursorRequest(
                null, null, 2, "DESCENDING", "createdAt");

            Notification n1 = mockNotification(UUID.randomUUID(), "알림1", "내용", NotificationLevel.INFO, Instant.now());
            Notification n2 = mockNotification(UUID.randomUUID(), "알림2", "내용", NotificationLevel.INFO, Instant.now());

            given(notificationRepository.findNotificationByCursor(eq(receiverId), isNull(), isNull(), eq(2)))
                .willReturn(List.of(n1, n2));

            // when
            CursorResponseNotificationDto result = notificationService.getNotifications(receiverId, request);

            // then
            assertThat(result.data()).hasSize(2);
            assertThat(result.hasNext()).isFalse();
            assertThat(result.nextCursor()).isNull();
            assertThat(result.nextIdAfter()).isNull();
        }

        @Test
        @DisplayName("성공: 알림이 없으면 빈 data와 hasNext=false, totalCount=0이 반환된다.")
        void success_empty_result() {
            // given
            NotificationCursorRequest request = new NotificationCursorRequest(
                null, null, 20, "DESCENDING", "createdAt");

            given(notificationRepository.findNotificationByCursor(eq(receiverId), isNull(), isNull(), eq(20)))
                .willReturn(List.of());

            // when
            CursorResponseNotificationDto result = notificationService.getNotifications(receiverId, request);

            // then
            assertThat(result.data()).isEmpty();
            assertThat(result.hasNext()).isFalse();
            assertThat(result.nextCursor()).isNull();
            assertThat(result.nextIdAfter()).isNull();
            assertThat(result.totalCount()).isZero();
        }

        @Test
        @DisplayName("성공: cursor가 있으면 Instant로 변환되어 Repository에 전달된다.")
        void success_cursor_converted_to_instant_and_passed_to_repository() {
            // given
            String cursorStr = "2025-08-01T10:00:00Z";
            UUID idAfter = UUID.randomUUID();
            NotificationCursorRequest request = new NotificationCursorRequest(
                cursorStr, idAfter, 20, "DESCENDING", "createdAt");

            given(notificationRepository.findNotificationByCursor(
                eq(receiverId), eq(Instant.parse(cursorStr)), eq(idAfter), eq(20)))
                .willReturn(List.of());

            // when
            CursorResponseNotificationDto result = notificationService.getNotifications(receiverId, request);

            // then - cursor 문자열이 정확한 Instant 값으로 변환되어 Repository에 전달됨
            assertThat(result.data()).isEmpty();
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("실패: cursor 형식이 잘못되면 NotificationException이 발생한다.")
        void fail_throws_exception_when_cursor_format_is_invalid() {
            // given
            NotificationCursorRequest request = new NotificationCursorRequest(
                "invalid-date", UUID.randomUUID(), 20, "DESCENDING", "createdAt");

            // when & then — parseCursorToInstant()에서 예외 발생, Repository는 호출되지 않음
            assertThatThrownBy(() -> notificationService.getNotifications(receiverId, request))
                .isInstanceOf(NotificationException.class);
        }

        @Test
        @DisplayName("성공: limit이 기본값(20)이고 결과가 1개이면 hasNext=false이다.")
        void success_single_result_has_next_false() {
            // given
            NotificationCursorRequest request = new NotificationCursorRequest(
                null, null, 20, "DESCENDING", "createdAt");

            UUID notifId = UUID.randomUUID();
            Instant createdAt = Instant.parse("2025-08-01T09:00:00Z");
            Notification n1 = mockNotification(notifId, "단건 알림", "내용", NotificationLevel.INFO, createdAt);

            given(notificationRepository.findNotificationByCursor(eq(receiverId), isNull(), isNull(), eq(20)))
                .willReturn(List.of(n1));
            given(notificationRepository.countByReceiver_Id(receiverId)).willReturn(1L);

            // when
            CursorResponseNotificationDto result = notificationService.getNotifications(receiverId, request);

            // then
            assertThat(result.data()).hasSize(1);
            assertThat(result.hasNext()).isFalse();
            assertThat(result.totalCount()).isEqualTo(1);
            assertThat(result.data().get(0).id()).isEqualTo(notifId);
            assertThat(result.data().get(0).createdAt()).isEqualTo(createdAt);
        }
    }

    private Notification mockNotification(UUID id, String title, String content,
        NotificationLevel level, Instant createdAt) {
        Notification notification = mock(Notification.class);
        User receiver = mock(User.class);
        given(notification.getId()).willReturn(id);
        given(notification.getTitle()).willReturn(title);
        given(notification.getContent()).willReturn(content);
        given(notification.getLevel()).willReturn(level);
        given(notification.getCreatedAt()).willReturn(createdAt);
        given(notification.getReceiver()).willReturn(receiver);
        given(receiver.getId()).willReturn(receiverId);
        return notification;
    }
}
