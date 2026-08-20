package com.codeit.mople.realtime.config;

import com.codeit.mople.realtime.session.LocalWebSocketSessionRegistry;
import com.codeit.mople.realtime.session.WebSocketForceDisconnectListener;
import com.codeit.mople.realtime.session.WebSocketSessionRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebSocketSessionTrackingConfig {

  private final LocalWebSocketSessionRegistry localRegistry;
  private final WebSocketSessionRegistryService registryService;

  // 원본 WebSocketSession은 이 인스턴스의 메모리에만 존재하므로, 소켓 라이프사이클
  // 훅(afterConnectionEstablished/Closed)에서 직접 로컬 레지스트리에 등록/해제한다.
  @Bean
  public WebSocketHandlerDecoratorFactory webSocketSessionTrackingDecoratorFactory() {
    return handler -> new WebSocketHandlerDecorator(handler) {

      @Override
      public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        localRegistry.registerConnection(session);
        super.afterConnectionEstablished(session);
      }

      @Override
      public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
          throws Exception {
        // 정상 종료/네트워크 끊김/우리가 강제로 close()한 경우 모두 이 콜백 하나로 통일된다.
        registryService.removeSession(session.getId());
        super.afterConnectionClosed(session, closeStatus);
      }
    };
  }

  // isAutoStartup()을 오버라이드해 꺼서, 컨텍스트 기동 과정(SmartLifecycle)에서 즉시 Redis
  // 연결을 시도하지 않게 한다(세터가 따로 없어 서브클래싱으로 처리). 그렇지 않으면 Redis가
  // 없는 환경에서 이 빈 하나 때문에 애플리케이션 전체가 기동조차 못 하게 된다(강제 로그아웃은
  // 부가 기능일 뿐 핵심 기능이 아님). 대신 ApplicationReadyEvent 시점에 별도로 시작하고,
  // 실패해도 애플리케이션은 계속 뜨게 한다.
  @Bean
  public RedisMessageListenerContainer webSocketForceDisconnectListenerContainer(
      RedisConnectionFactory connectionFactory, WebSocketForceDisconnectListener listener) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer() {
      @Override
      public boolean isAutoStartup() {
        return false;
      }
    };
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(listener,
        new ChannelTopic(WebSocketSessionRegistryService.FORCE_DISCONNECT_CHANNEL));
    return container;
  }

  @Bean
  public ApplicationListener<ApplicationReadyEvent> webSocketForceDisconnectListenerStarter(
      RedisMessageListenerContainer webSocketForceDisconnectListenerContainer) {
    return event -> {
      try {
        webSocketForceDisconnectListenerContainer.start();
      } catch (Exception e) {
        log.error("Redis 강제 종료 pub/sub 리스너 시작 실패 - 강제 로그아웃 실시간 종료 기능이 "
            + "비활성화됩니다 (나머지 애플리케이션은 정상 동작)", e);
      }
    };
  }

  // 기동 시점 로그만으로는 이후 이 인스턴스가 계속 강제 종료 불능 상태인지 알 수 없어,
  // /actuator/health로 지속적으로 상태를 노출한다.
  @Bean
  public HealthIndicator webSocketForceDisconnectHealthIndicator(
      RedisMessageListenerContainer webSocketForceDisconnectListenerContainer) {
    // isRunning()은 컨테이너 라이프사이클(start/stop) 여부만 반영해 재연결 중에도 true로 남아있을
    // 수 있으므로, 실제 Redis 구독이 확정된 상태를 뜻하는 isListening()으로 판단한다.
    return () -> webSocketForceDisconnectListenerContainer.isListening()
        ? Health.up().build()
        : Health.down()
            .withDetail("reason", "강제 로그아웃 Redis 리스너가 구독 중이 아님(이 인스턴스는 실시간 강제 종료 불가)")
            .build();
  }
}
