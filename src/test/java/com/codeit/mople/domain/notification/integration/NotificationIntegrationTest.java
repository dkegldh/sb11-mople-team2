package com.codeit.mople.domain.notification.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.notification.entity.NotificationType;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.global.config.SecurityConfig;
import com.codeit.mople.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@Import(SecurityConfig.class)
@Transactional
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("알림 커서 페이지네이션 통합 테스트")
public class NotificationIntegrationTest {

    // NotificationController에서 사용하는 임시 receiverId
    private static final UUID TEMP_RECEIVER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtProvider jwtProvider;

    @PersistenceContext
    private EntityManager entityManager;

    private CustomUserDetails principal;
    private int alertCounter = 0;

    @BeforeEach
    void setUp() {
        alertCounter = 0;
        // TEMP_RECEIVER_ID를 가진 User를 native SQL로 삽입 (UUID auto-generate 우회)
        entityManager.createNativeQuery(
            "INSERT INTO users (id, email, password, name, role, locked, session_version, created_at) " +
            "VALUES (:id, 'temp@test.com', 'password', '임시유저', 'USER', false, 0, CURRENT_TIMESTAMP)"
        ).setParameter("id", TEMP_RECEIVER_ID).executeUpdate();

        principal = new CustomUserDetails(TEMP_RECEIVER_ID, Role.USER);
    }

    // 알림마다 1초씩 다른 명시적 timestamp를 사용해 CURRENT_TIMESTAMP 정밀도 문제 방지
    private void 알림_생성(String title, NotificationType type) {
        alertCounter++;
        Instant createdAt = Instant.EPOCH.plusSeconds(alertCounter);
        entityManager.createNativeQuery(
            "INSERT INTO notifications (id, receiver_id, title, content, level, notification_type, created_at) " +
            "VALUES (RANDOM_UUID(), :receiverId, :title, '내용', 'INFO', :type, :createdAt)"
        )
        .setParameter("receiverId", TEMP_RECEIVER_ID)
        .setParameter("title", title)
        .setParameter("type", type.name())
        .setParameter("createdAt", createdAt)
        .executeUpdate();
    }

    @Nested
    @DisplayName("GET /api/notifications - 첫 페이지 조회")
    class FirstPage {

        @Test
        @DisplayName("성공: 알림이 없으면 200과 빈 data가 반환된다.")
        void success_empty_notifications() throws Exception {
            mockMvc.perform(get("/api/notifications")
                    .param("limit", "20")
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.sortBy").value("createdAt"))
                .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));
        }

        @Test
        @DisplayName("성공: 알림이 있으면 200과 최신순 알림 목록이 반환된다.")
        void success_returns_notifications_in_descending_order() throws Exception {
            알림_생성("오래된 알림", NotificationType.NEW_FOLLOWER);
            알림_생성("중간 알림", NotificationType.PLAYLIST_SUBSCRIBE);
            알림_생성("최신 알림", NotificationType.DIRECT_MESSAGE);

            mockMvc.perform(get("/api/notifications")
                    .param("limit", "20")
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].title").value("최신 알림"))
                .andExpect(jsonPath("$.data[1].title").value("중간 알림"))
                .andExpect(jsonPath("$.data[2].title").value("오래된 알림"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(3));
        }

        @Test
        @DisplayName("성공: 알림이 limit+1개 이상이면 hasNext=true이고 nextCursor가 반환된다.")
        void success_has_next_when_more_than_limit() throws Exception {
            알림_생성("알림1", NotificationType.NEW_FOLLOWER);
            알림_생성("알림2", NotificationType.NEW_FOLLOWER);
            알림_생성("알림3", NotificationType.NEW_FOLLOWER);

            mockMvc.perform(get("/api/notifications")
                    .param("limit", "2")
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.nextIdAfter").isNotEmpty());
        }

        @Test
        @DisplayName("성공: 각 알림의 응답 필드가 올바르게 반환된다.")
        void success_notification_fields_are_correct() throws Exception {
            알림_생성("팔로우 알림", NotificationType.NEW_FOLLOWER);

            mockMvc.perform(get("/api/notifications")
                    .param("limit", "20")
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").isNotEmpty())
                .andExpect(jsonPath("$.data[0].title").value("팔로우 알림"))
                .andExpect(jsonPath("$.data[0].content").value("내용"))
                .andExpect(jsonPath("$.data[0].level").value("INFO"))
                .andExpect(jsonPath("$.data[0].receiverId").value(TEMP_RECEIVER_ID.toString()))
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("GET /api/notifications - 커서 기반 다음 페이지 조회")
    class NextPage {

        @Test
        @DisplayName("성공: 첫 페이지의 nextCursor로 다음 페이지를 조회하면 이전에 없던 알림이 반환된다.")
        void success_next_page_with_cursor() throws Exception {
            알림_생성("알림1", NotificationType.NEW_FOLLOWER);
            알림_생성("알림2", NotificationType.PLAYLIST_SUBSCRIBE);
            알림_생성("알림3", NotificationType.DIRECT_MESSAGE);

            // 첫 페이지 (limit=2) → 알림3, 알림2 반환
            MvcResult firstResult = mockMvc.perform(get("/api/notifications")
                    .param("limit", "2")
                    .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].title").value("알림3"))
                .andExpect(jsonPath("$.data[1].title").value("알림2"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn();

            String body = firstResult.getResponse().getContentAsString();
            String nextCursor = objectMapper.readTree(body).get("nextCursor").asText();
            String nextIdAfter = objectMapper.readTree(body).get("nextIdAfter").asText();

            // 두 번째 페이지 → 알림1만 반환
            mockMvc.perform(get("/api/notifications")
                    .param("limit", "2")
                    .param("cursor", nextCursor)
                    .param("idAfter", nextIdAfter)
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("알림1"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.nextIdAfter").doesNotExist());
        }

        @Test
        @DisplayName("성공: 마지막 페이지에서 cursor로 조회하면 빈 data와 hasNext=false가 반환된다.")
        void success_empty_when_no_more_data() throws Exception {
            알림_생성("알림1", NotificationType.NEW_FOLLOWER);

            // 첫 페이지 조회
            MvcResult firstResult = mockMvc.perform(get("/api/notifications")
                    .param("limit", "1")
                    .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andReturn();

            String body = firstResult.getResponse().getContentAsString();
            String firstCreatedAt = objectMapper.readTree(body)
                .get("data").get(0).get("createdAt").asText();
            String firstId = objectMapper.readTree(body)
                .get("data").get(0).get("id").asText();

            // 해당 알림을 cursor로 사용해 다음 페이지 요청 → 빈 결과
            mockMvc.perform(get("/api/notifications")
                    .param("limit", "20")
                    .param("cursor", firstCreatedAt)
                    .param("idAfter", firstId)
                    .with(user(principal)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
        }
    }
}
