package com.codeit.mople.domain.content.client.tmdb.config;

import com.codeit.mople.domain.content.exception.ContentErrorCode;
import com.codeit.mople.domain.content.exception.ContentException;
import feign.Response;
import feign.RetryableException;
import feign.Util;
import feign.codec.ErrorDecoder;
import java.util.Collection;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

// ErrorDecoder Feign에서 제공하는 인터페이스임 외부 API를 호출했을때 응답이 에러(4xx,5xx)인 경우 어떻게 처리할지 커스텀
@Slf4j
public class TmdbErrorDecoder implements ErrorDecoder {

  private static final int MAX_BODY_LOG_LENGTH = 500;
  private static final String RETRY_AFTER_HEADER = "retry-after";

  // methodKey: 어떤 Feign 인터페이스의 어떤 메서드가 호출되다가 에러가 났는지 식별
  // response: 외부 API의 실제 Http 응답(status, body, headers 등)
  @Override
  public Exception decode(String methodKey, Response response) {
    int status = response.status();
    String body = readBody(response);

    if (isRetryable(status)) {
      Long retryAfter = parseRetryAfter(response);
      log.warn("TMDB 일시적 실패: method={}, status={}, retryAfter={}, body={}",
          methodKey, status, retryAfter, body);

      return new RetryableException(
          status,                                   // http 상태코드
          "TMDB 일시적 오류: status= " + status,       // 메시지
          response.request().httpMethod(),          // http 메서드
          retryAfter,                               // 에러가 터진 후 재시도 시간
          response.request());                      // 내가 요청한 request
    }

    log.error("TMDB 호출 실패: method={}, status={}, body={}", methodKey, status, body);
    return new ContentException(toErrorCode(status),
        Map.of("status", status, "method", methodKey));
  }

  private boolean isRetryable(int status) {
    return status == 429 || (status >= 500 && status != 501);
  }

  private ContentErrorCode toErrorCode(int status) {
    return switch (status) {
      case 401 -> ContentErrorCode.TMDB_UNAUTHORIZED;
      case 404 -> ContentErrorCode.TMDB_CONTENT_NOT_FOUND;
      default -> ContentErrorCode.TMDB_REQUEST_FAILED;
    };
  }

  private Long parseRetryAfter(Response response) {
    Collection<String> values = response.headers().get(RETRY_AFTER_HEADER);

    if (values == null || values.isEmpty()) {
      return null;
    }

    try {
      long seconds = Long.parseLong(values.iterator().next().trim());
      return seconds < 0 ? null : System.currentTimeMillis() + seconds * 1000L;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private String readBody(Response response) {
    if (response.body() == null) {
      return "";
    }
    try {
      String body = Util.toString(response.body().asReader(Util.UTF_8));
      return body.length() > MAX_BODY_LOG_LENGTH
          ? body.substring(0, MAX_BODY_LOG_LENGTH) + "...(truncated)"
          : body;
    } catch (Exception e) {
      log.debug("TMDB 응답 본문을 읽지 못했습니다.", e);
      return "";
    }
  }
}
