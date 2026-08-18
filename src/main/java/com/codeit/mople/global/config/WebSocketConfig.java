package com.codeit.mople.global.config;

import com.codeit.mople.domain.auth.security.JwtChannelInterceptor;
import com.codeit.mople.global.error.CustomStompErrorHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketConfig.WebSocketProperties.class) // Properties 빈 활성화
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final JwtChannelInterceptor jwtChannelInterceptor;
  private final ObjectMapper objectMapper;
  private final CustomStompErrorHandler customStompErrorHandler;
  private final WebSocketProperties webSocketProperties;

  @Qualifier("webSocketSessionTrackingDecoratorFactory")
  private final WebSocketHandlerDecoratorFactory webSocketSessionTrackingDecoratorFactory;

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    String[] origins = webSocketProperties.getAllowedOrigins().toArray(new String[0]);

    registry.addEndpoint("/ws")
        // TODO: 운영 단계에서는 실제 도메인(또는 IP) 주소 명시
        .setAllowedOrigins(origins)
        .withSockJS();

    registry.setErrorHandler(customStompErrorHandler);
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/sub", "/queue");
    registry.setApplicationDestinationPrefixes("/pub");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(jwtChannelInterceptor);
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
    registry.addDecoratorFactory(webSocketSessionTrackingDecoratorFactory);
  }

  @Override
  public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
    MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
    converter.setObjectMapper(objectMapper);

    //프론트엔드가 ContentType 헤더를 안 보내도 무조건 JSON으로 인식하도록 강제
    DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
    resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);
    converter.setContentTypeResolver(resolver);

    messageConverters.add(0, converter);
    return false;
  }

  // yaml 파일에 값이 없을 때 사용할 디폴트 값 설정
  @Getter
  @Setter
  @ConfigurationProperties(prefix = "app.websocket")
  public static class WebSocketProperties {
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:8080"));
  }
}