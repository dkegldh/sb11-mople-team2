package com.codeit.mople.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mople.global.event.failure.ConsumeFailureMetricsListener;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.ResolvableType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;

// 자동 설정이 만들어줄 기본 KafkaTemplate을 직접 작성,정의한 빈으로 밀어내는 구조라,
// enabled=true일 때 템플릿 두 개가 제대로 올라오는지, enabled=false일 때는 자동 설정으로 잘 돌아가는지 확인하는 테스트
@DisplayName("KafkaConfig 컨텍스트 테스트")
class KafkaConfigContextTest {

  static final String BOOTSTRAP_SERVERS = "localhost:9092";

  static KafkaProperties kafkaProperties() {
    return new KafkaProperties(true, BOOTSTRAP_SERVERS, new KafkaProperties.Topics(
        "mople.follow.created.v1",
        "mople.playlist-events.v1",
        "mople.playlist.content-added.v1",
        "mople.review.created.v1",
        "mople.review.updated.v1",
        "mople.review.deleted.v1",
        "mople.direct-message.created.v1",
        "mople.notification.created.v1",
        "mople.content-search-index-events.v1",
        "mople.user-search-index-events.v1",
        "mople.playlist-search-index-events.v1"
    ));
  }

  final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
      .withUserConfiguration(KafkaConfig.class)
      .withBean(KafkaProperties.class, KafkaConfigContextTest::kafkaProperties)
      .withBean(ConsumeFailureMetricsListener.class,
          () -> new ConsumeFailureMetricsListener(new SimpleMeterRegistry()))
      .withPropertyValues(
          "spring.kafka.enabled=true",
          "spring.kafka.bootstrap-servers=" + BOOTSTRAP_SERVERS,
          "spring.kafka.producer.key-serializer="
              + "org.apache.kafka.common.serialization.StringSerializer",
          "spring.kafka.producer.value-serializer="
              + "org.springframework.kafka.support.serializer.JsonSerializer");

  @Nested
  @DisplayName("템플릿 빈 구성")
  class Templates {

    @Test
    @DisplayName("자동 설정 대신 우리 템플릿 두 개가 올라오는지")
    void registersBothTemplates() {
      runner.run(context -> assertThat(context)
          .hasBean("kafkaTemplate")
          .hasBean("bytesKafkaTemplate")
          .hasSingleBean(ProducerFactory.class)
          .hasSingleBean(DefaultErrorHandler.class));
    }

    @Test
    @DisplayName("KafkaTemplate<String, Object> 주입이 bytes 템플릿과 안 섞이는지")
    void objectTemplateResolvesUnambiguously() {
      runner.run(context -> assertThat(context.getBeanNamesForType(
          ResolvableType.forClassWithGenerics(KafkaTemplate.class, String.class, Object.class)))
          .containsExactly("kafkaTemplate"));
    }

    @Test
    @DisplayName("KafkaTemplate<String, byte[]> 주입이 일반 템플릿과 안 섞이는지")
    void bytesTemplateResolvesUnambiguously() {
      runner.run(context -> assertThat(context.getBeanNamesForType(
          ResolvableType.forClassWithGenerics(KafkaTemplate.class, String.class, byte[].class)))
          .containsExactly("bytesKafkaTemplate"));
    }

    @Test
    @DisplayName("kafka.enabled 가 꺼져 있으면 자동 설정 템플릿으로 되돌아가는지")
    void fallsBackToAutoConfigurationWhenDisabled() {
      runner.withPropertyValues("spring.kafka.enabled=false")
          .run(context -> assertThat(context)
              .hasSingleBean(KafkaTemplate.class)
              .doesNotHaveBean("bytesKafkaTemplate")
              .doesNotHaveBean(DefaultErrorHandler.class));
    }
  }
}