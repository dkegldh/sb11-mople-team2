package com.codeit.mople.domain.content.client.tmdb.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "external-api.tmdb")
public record TmdbProperties(

    @NotBlank(message = "TMDB API 키가 없습니다.")
    String apiKey,
    String imageBaseUrl
) {

}
