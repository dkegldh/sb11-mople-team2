package com.codeit.mople.global.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "spring.kafka")
public record KafkaProperties(
    boolean enabled,
    @NotNull @Valid Topics topics
) {
  public record Topics(
      @NotBlank String followCreated,
      @NotBlank String playlistContentAdded
  ) {}
}
