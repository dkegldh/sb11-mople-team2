package com.codeit.mople.global.sse;

public final class SseStreamKeys {

  private SseStreamKeys() {}

  // 서버 간 SSE 이벤트 전달을 위한 Redis Stream 키
  public static final String STREAM_KEY = "sse:events:";

  // 최종 실패 시 별도로 보관하는 Redis Stream 키
  public static final String FAILED_STREAM_KEY = "sse:events:failed:";

}
