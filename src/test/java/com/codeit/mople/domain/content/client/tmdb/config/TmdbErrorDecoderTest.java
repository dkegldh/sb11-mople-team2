package com.codeit.mople.domain.content.client.tmdb.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mople.domain.content.exception.ContentErrorCode;
import com.codeit.mople.domain.content.exception.ContentException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestTemplate;
import feign.Response;
import feign.RetryableException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("TmdbErorrDecoder 단위 테스트")
public class TmdbErrorDecoderTest {

  private static final String METHOD_KEY = "TmdbClient#getPopularMovies(int)";
  private static final String URL = "https://api.themviedb.org/3/movie/popular?page=1";
  private static final String ERROR_BODY = """
      {"staus_code":7,"status_message":Invalid API key."}
      """;

  private final TmdbErrorDecoder decoder = new TmdbErrorDecoder();

  @ParameterizedTest(name = "status={0} -> {1}")
  @CsvSource({
      "401, TMDB_UNAUTHORIZED",
      "404, TMDB_CONTENT_NOT_FOUND",
      "400, TMDB_REQUEST_FAILED",
      "501, TMDB_REQUEST_FAILED"
  })
  @DisplayName("재시도 대상이 아닌 응답은 상태코드에 맞는 ContentException으로 반환")
  void decode_NonRetryableStatus_ContentException(int status, ContentErrorCode expected) {
    Exception exception = decoder.decode(METHOD_KEY, response(status));

    assertThat(exception).isInstanceOf(ContentException.class);
    assertThat(((ContentException) exception).getErrorCode()).isEqualTo(expected);
  }

  @ParameterizedTest(name = "status={0}")
  @ValueSource(ints = {429, 500})
  @DisplayName("429와 501을 제외한 5xx는 RetryableException으로 반환")
  void decode_RetryableStatus_RetryableException(int status) {
    Exception exception = decoder.decode(METHOD_KEY, response(status));

    assertThat(exception).isInstanceOf(RetryableException.class);
    assertThat(((RetryableException) exception).status()).isEqualTo(status);
  }

  @Test
  @DisplayName("Retry-After 헤더가 있으면 재시도 시각이 현재 시각 + 초로 계산")
  void decode_WithRetryAfterHeader_RetryAfterCalculated() {
    long before = System.currentTimeMillis();

    Exception exception = decoder.decode(METHOD_KEY, response(429, Map.of("Retry-After", List.of("10"))));

    assertThat(((RetryableException) exception).retryAfter())
        .isNotNull()
        .isBetween(before + 10_000L, System.currentTimeMillis() + 10_000L);
  }

  private Response response(int status) {
    return response(status, Map.of());
  }

  private Response response(int status, Map<String, Collection<String>> headers) {
    return Response.builder()
        .status(status)
        .reason("error")
        .request(request())
        .headers(headers)
        .body(ERROR_BODY, StandardCharsets.UTF_8)
        .build();
  }

  private Request request() {
    return Request.create(
        HttpMethod.GET, URL, Map.of(), (byte[]) null, StandardCharsets.UTF_8, new RequestTemplate());
  }
}
