package com.codeit.mople.realtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.notification.repository.NotificationRepository;
import com.codeit.mople.domain.user.admin.service.AdminService;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.jwt.JwtProvider;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 관리자 강제 로그아웃 이벤트 발행 -> Redis pub/sub -> 실제 WebSocket 연결 종료까지
// 전체 경로를 실제 Redis(Testcontainers)와 실제 STOMP 연결로 검증한다.
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("WebSocket 강제 로그아웃 통합 테스트")
class WebSocketForceLogoutIntegrationTest {

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @LocalServerPort
  private int port;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private JwtProvider jwtProvider;

  @Autowired
  private SessionTokenRepository sessionTokenRepository;

  @Autowired
  private AdminService adminService;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private SimpMessagingTemplate messagingTemplate;

  private User targetUser;
  private WebSocketStompClient stompClient;

  @BeforeEach
  void setUp() {
    targetUser = userRepository.save(
        User.createUser("ws-force-logout@test.com", "encoded", "강제로그아웃대상유저"));

    // AdminService.validateNotSelf가 SecurityContext에서 관리자 인증 정보를 읽으므로 세팅해둔다.
    CustomUserDetails adminPrincipal = new CustomUserDetails(UUID.randomUUID(), Role.ADMIN);
    Authentication adminAuth = new UsernamePasswordAuthenticationToken(
        adminPrincipal, null, adminPrincipal.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(adminAuth);

    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new MappingJackson2MessageConverter());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    // 같은 UserAccountStatusChangedEvent를 NotificationEventListener도 비동기로 구독해 알림을
    // 저장한다. 그 저장이 끝나기 전에 유저를 지우면 FK 제약 위반이 나므로 먼저 기다린다.
    await().atMost(5, TimeUnit.SECONDS)
        .until(() -> notificationRepository.countByReceiver_Id(targetUser.getId()) >= 1);
    notificationRepository.deleteAll(notificationRepository.findAllByReceiver_Id(targetUser.getId()));
    userRepository.delete(targetUser);
  }

  @Test
  @DisplayName("계정 잠금 시 연결된 WebSocket 세션에 사유가 전달되고 연결이 종료된다")
  void accountLocked_notifiesAndClosesConnectedWebSocketSession() throws Exception {
    // given - 로그인 상태와 동일하게 access token 발급 + 세션 토큰 등록
    String jti = UUID.randomUUID().toString();
    String accessToken = jwtProvider.createAccessToken(targetUser.getId(), jti, targetUser.getRole());
    sessionTokenRepository.save(targetUser.getId(), jti, Duration.ofMinutes(30));

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("Authorization", "Bearer " + accessToken);

    CompletableFuture<Throwable> transportErrorFuture = new CompletableFuture<>();
    StompSession session = stompClient.connectAsync(
        "ws://localhost:" + port + "/ws/websocket",
        new WebSocketHttpHeaders(),
        connectHeaders,
        new StompSessionHandlerAdapter() {
          @Override
          public void handleTransportError(StompSession session, Throwable exception) {
            transportErrorFuture.complete(exception);
          }
        }
    ).get(5, TimeUnit.SECONDS);

    CompletableFuture<Map<String, String>> errorMessageFuture = new CompletableFuture<>();
    CompletableFuture<Void> subscriptionReadyFuture = new CompletableFuture<>();
    session.subscribe("/user/queue/errors", new StompFrameHandler() {
      @Override
      public Type getPayloadType(StompHeaders headers) {
        return Map.class;
      }

      @Override
      public void handleFrame(StompHeaders headers, Object payload) {
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) payload;
        if ("PROBE".equals(body.get("reason"))) {
          subscriptionReadyFuture.complete(null);
        } else {
          errorMessageFuture.complete(body);
        }
      }
    });
    // 고정 시간 대기 대신, SUBSCRIBE가 실제로 반영됐는지 같은 경로(convertAndSendToUser →
    // /queue/errors)로 프로브 메시지를 보내 확인한다. 아직 반영 전이면 프로브가 유실되므로,
    // 반영될 때까지 재전송한다.
    await().atMost(5, TimeUnit.SECONDS).until(() -> {
      messagingTemplate.convertAndSendToUser(
          targetUser.getId().toString(), "/queue/errors", Map.of("reason", "PROBE"));
      return subscriptionReadyFuture.isDone();
    });

    // when
    adminService.changeUserLocked(targetUser.getId(), true);

    // then
    Map<String, String> errorMessage = errorMessageFuture.get(5, TimeUnit.SECONDS);
    assertThat(errorMessage.get("reason")).isEqualTo("계정이 잠금 처리되어 연결이 종료되었습니다.");

    await().atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(session.isConnected()).isFalse());
  }
}
