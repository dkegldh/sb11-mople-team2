package com.codeit.mople.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.kafka", name = "enabled", havingValue = "true")
public class KafkaConfig {

  public KafkaConfig(@Value("${spring.kafka.bootstrap-servers:}") String bootstrapServers) {
    Assert.state(StringUtils.hasText(bootstrapServers),
        "kafka.enabled=true 이지만 spring.kafka.bootstrap-servers 가 비어있습니다. KAFKA_BOOTSTRAP_SERVERS 를 설정하세요.");
    log.info("Kafka 이벤트 발행 활성화: bootstrapServers={}", bootstrapServers);
  }
}
