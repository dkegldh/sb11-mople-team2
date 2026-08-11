package com.codeit.mople.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.auth.security.CustomOAuth2UserService;
import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.auth.security.handler.OAuth2FailureHandler;
import com.codeit.mople.domain.auth.security.handler.OAuth2SuccessHandler;
import com.codeit.mople.domain.notification.dto.response.CursorResponseNotificationDto;
import com.codeit.mople.domain.notification.exception.NotificationErrorCode;
import com.codeit.mople.domain.notification.exception.NotificationException;
import com.codeit.mople.domain.notification.dto.response.NotificationResponse;
import com.codeit.mople.domain.notification.NotificationLevel;
import com.codeit.mople.domain.notification.service.NotificationService;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.config.SecurityConfig;
import com.codeit.mople.global.jwt.JwtProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
@DisplayName("NotificationController 테스트")
class NotificationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    NotificationService notificationService;

    @MockitoBean
    JwtProvider jwtProvider;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    OAuth2SuccessHandler oAuth2SuccessHandler;

    @MockitoBean
    OAuth2FailureHandler oAuth2FailureHandler;

    CustomUserDetails principal;

    @BeforeEach
    void setUp() {
        principal = new CustomUserDetails(UUID.randomUUID(), Role.USER);
    }

    @Nested
    @DisplayName("GET /api/notifications - 알림 목록 커서 조회")
    class GetNotifications {

        @Test
        @DisplayName("성공: 유효한 파라미터로 요청하면 200과 알림 목록이 반환된다.")
        void success_200_with_notification_list() throws Exception {
            // given
            UUID notifId = UUID.randomUUID();
            Instant createdAt = Instant.parse("2025-08-01T10:00:00Z");
            NotificationResponse notifResponse = new NotificationResponse(
                notifId, createdAt, principal.getUserId(), "팔로우 알림", "홍길동님이 팔로우했습니다.", NotificationLevel.INFO);

            CursorResponseNotificationDto response = new CursorResponseNotificationDto(
                List.of(notifResponse), null, null, false, 1, "createdAt", "DESCENDING");

            given(notificationService.getNotifications(any(), any())).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/notifications")
                    .param("limit", "20")
                    .param("sortDirection", "DESCENDING")
                    .param("sortBy", "createdAt")
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(notifId.toString()))
                .andExpect(jsonPath("$.data[0].title").value("팔로우 알림"))
                .andExpect(jsonPath("$.data[0].content").value("홍길동님이 팔로우했습니다."))
                .andExpect(jsonPath("$.data[0].level").value("INFO"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.nextIdAfter").doesNotExist())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.sortBy").value("createdAt"))
                .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));

            then(notificationService).should().getNotifications(eq(principal.getUserId()), any());
        }

        @Test
        @DisplayName("성공: 알림이 없으면 200과 빈 data 목록이 반환된다.")
        void success_200_with_empty_list() throws Exception {
            // given
            CursorResponseNotificationDto response = new CursorResponseNotificationDto(
                List.of(), null, null, false, 0, "createdAt", "DESCENDING");

            given(notificationService.getNotifications(any(), any())).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/notifications")
                    .param("limit", "20")
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(0));

            then(notificationService).should().getNotifications(eq(principal.getUserId()), any());
        }

        @Test
        @DisplayName("성공: 다음 페이지가 있으면 200과 nextCursor, nextIdAfter가 반환된다.")
        void success_200_with_next_cursor() throws Exception {
            // given
            String nextCursor = "2025-08-01T09:00:00Z";
            UUID nextIdAfter = UUID.randomUUID();
            CursorResponseNotificationDto response = new CursorResponseNotificationDto(
                List.of(), nextCursor, nextIdAfter, true, 20, "createdAt", "DESCENDING");

            given(notificationService.getNotifications(any(), any())).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/notifications")
                    .param("limit", "20")
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value(nextCursor))
                .andExpect(jsonPath("$.nextIdAfter").value(nextIdAfter.toString()));

            then(notificationService).should().getNotifications(eq(principal.getUserId()), any());
        }

        @Test
        @DisplayName("실패: limit이 없으면 400을 반환한다.")
        void fail_400_when_limit_is_missing() throws Exception {
            // when & then
            mockMvc.perform(get("/api/notifications")
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-001"));

            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("실패: limit이 0이면 400을 반환한다.")
        void fail_400_when_limit_is_zero() throws Exception {
            // when & then
            mockMvc.perform(get("/api/notifications")
                    .param("limit", "0")
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-001"));

            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("실패: limit이 101이면 400을 반환한다.")
        void fail_400_when_limit_exceeds_max() throws Exception {
            // when & then
            mockMvc.perform(get("/api/notifications")
                    .param("limit", "101")
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-001"));

            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("실패: sortDirection이 ASCENDING이면 400을 반환한다.")
        void fail_400_when_sort_direction_is_ascending() throws Exception {
            // when & then
            mockMvc.perform(get("/api/notifications")
                    .param("limit", "20")
                    .param("sortDirection", "ASCENDING")
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-001"));

            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("실패: sortBy가 createdAt이 아니면 400을 반환한다.")
        void fail_400_when_sort_by_is_invalid() throws Exception {
            // when & then
            mockMvc.perform(get("/api/notifications")
                    .param("limit", "20")
                    .param("sortBy", "updatedAt")
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-001"));

            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("실패: cursor만 있고 idAfter가 없으면 400을 반환한다.")
        void fail_400_when_cursor_without_id_after() throws Exception {
            // when & then
            mockMvc.perform(get("/api/notifications")
                    .param("limit", "20")
                    .param("cursor", "2025-08-01T10:00:00Z")
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-001"));

            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("성공: limit이 1(최솟값)이면 200을 반환한다.")
        void success_200_when_limit_is_min() throws Exception {
            given(notificationService.getNotifications(any(), any())).willReturn(
                new CursorResponseNotificationDto(List.of(), null, null, false, 0, "createdAt", "DESCENDING"));

            mockMvc.perform(get("/api/notifications")
                    .param("limit", "1")
                    .with(user(principal)))
                .andExpect(status().isOk());

            then(notificationService).should().getNotifications(eq(principal.getUserId()), any());
        }

        @Test
        @DisplayName("성공: limit이 100(최댓값)이면 200을 반환한다.")
        void success_200_when_limit_is_max() throws Exception {
            given(notificationService.getNotifications(any(), any())).willReturn(
                new CursorResponseNotificationDto(List.of(), null, null, false, 0, "createdAt", "DESCENDING"));

            mockMvc.perform(get("/api/notifications")
                    .param("limit", "100")
                    .with(user(principal)))
                .andExpect(status().isOk());

            then(notificationService).should().getNotifications(eq(principal.getUserId()), any());
        }

        @Test
        @DisplayName("성공: cursor와 idAfter를 함께 전달하면 200을 반환한다.")
        void success_200_with_cursor_and_id_after() throws Exception {
            String cursor = "2025-08-01T10:00:00Z";
            UUID idAfter = UUID.randomUUID();
            given(notificationService.getNotifications(any(), any())).willReturn(
                new CursorResponseNotificationDto(List.of(), null, null, false, 0, "createdAt", "DESCENDING"));

            mockMvc.perform(get("/api/notifications")
                    .param("limit", "20")
                    .param("cursor", cursor)
                    .param("idAfter", idAfter.toString())
                    .with(user(principal)))
                .andExpect(status().isOk());

            then(notificationService).should().getNotifications(eq(principal.getUserId()), any());
        }

        @Test
        @DisplayName("실패: idAfter만 있고 cursor가 없으면 400을 반환한다.")
        void fail_400_when_id_after_without_cursor() throws Exception {
            mockMvc.perform(get("/api/notifications")
                    .param("limit", "20")
                    .param("idAfter", UUID.randomUUID().toString())
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-001"));

            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("실패: 인증 없이 요청하면 401을 반환한다.")
        void fail_401_when_unauthenticated() throws Exception {
            // when & then
            mockMvc.perform(get("/api/notifications")
                    .param("limit", "20"))
                .andDo(print())
                .andExpect(status().isUnauthorized());

            verifyNoInteractions(notificationService);
        }
    }

    @Nested
    @DisplayName("DELETE /api/notifications/{notificationId} - 알림 삭제")
    class DeleteNotification {

        @Test
        @DisplayName("성공: 본인 알림을 삭제하면 204를 반환한다.")
        void success_204_when_delete_notification() throws Exception {
            // given
            UUID notificationId = UUID.randomUUID();
            willDoNothing().given(notificationService).deleteNotification(any(), any());

            // when & then
            mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId)
                    .with(user(principal))
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isNoContent());

            then(notificationService).should()
                .deleteNotification(eq(notificationId), eq(principal.getUserId()));
        }

        @Test
        @DisplayName("실패: 인증 없이 요청하면 401을 반환한다.")
        void fail_401_when_unauthenticated() throws Exception {
            // when & then
            mockMvc.perform(delete("/api/notifications/{notificationId}", UUID.randomUUID())
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isUnauthorized());

            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 알림이면 404를 반환한다.")
        void fail_404_when_notification_not_found() throws Exception {
            // given
            UUID notificationId = UUID.randomUUID();
            willThrow(new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))
                .given(notificationService).deleteNotification(any(), any());

            // when & then
            mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId)
                    .with(user(principal))
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION-001"));
        }

        @Test
        @DisplayName("실패: 본인 알림이 아니면 403을 반환한다.")
        void fail_403_when_not_owner() throws Exception {
            // given
            UUID notificationId = UUID.randomUUID();
            willThrow(new NotificationException(NotificationErrorCode.NOTIFICATION_FORBIDDEN))
                .given(notificationService).deleteNotification(any(), any());

            // when & then
            mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId)
                    .with(user(principal))
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION-002"));
        }

        @Test
        @DisplayName("실패: notificationId가 UUID 형식이 아니면 400을 반환한다.")
        void fail_400_when_notification_id_is_not_uuid() throws Exception {
            // when & then
            mockMvc.perform(delete("/api/notifications/not-a-uuid")
                    .with(user(principal))
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-001"));

            verifyNoInteractions(notificationService);
        }
    }
}
