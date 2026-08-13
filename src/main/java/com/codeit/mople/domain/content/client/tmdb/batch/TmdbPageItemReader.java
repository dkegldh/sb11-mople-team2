package com.codeit.mople.domain.content.client.tmdb.batch;

import com.codeit.mople.domain.content.client.tmdb.dto.TmdbContentItem;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbPageResponse;
import com.codeit.mople.domain.content.exception.ContentErrorCode;
import com.codeit.mople.domain.content.exception.ContentException;
import feign.RetryableException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.IntFunction;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemStreamSupport;

// 청크 방식의 job이 시작할때 Reader
@Slf4j
public class TmdbPageItemReader extends ItemStreamSupport
    implements ItemStreamReader<TmdbContentItem> {

  private static final String CURSOR_KEY = "nextPage";

  public static final int PAGE_SIZE = 20;

  // movie, tv 구분할 필요 없게끔 IntFunction 사용
  private final IntFunction<TmdbPageResponse<? extends TmdbContentItem>> fetcher;

  // 내가 설정한 maxPages 값 넣는 용도
  private final int maxPages;

  // ArrayDeque는 0번째 칸이 비어있어도 됨 첫 번째 칸이 어디인지 head를 표시함
  // buffer: TMDB가 한번에 주는 데이터
  private final Deque<TmdbContentItem> buffer = new ArrayDeque<>();

  private int nextPage = 1;

  // tmdb가 알려주는 totalPage 하지만 응답 받기 전에는 모름
  private int totalPages = Integer.MAX_VALUE;

  public TmdbPageItemReader(
      String name,
      // tmdb에 page(1,2,3,..)를 요청하고 Http 요청하고 응답을 TmdbPageResponse로 변환 용도
      IntFunction<TmdbPageResponse<? extends TmdbContentItem>> fetcher, int maxPages) {
    setName(name);
    this.fetcher = fetcher;
    this.maxPages = maxPages;
  }

  // 1. executionContext에 "nextPage"라는 키가 있으면 값을 불러오고 없으면 "1" 할당
  // 2. buffer를 비움
  @Override
  public void open(ExecutionContext executionContext) {
    String key = getExecutionContextKey(CURSOR_KEY);
    nextPage = executionContext.containsKey(key) ? executionContext.getInt(key) : 1;
    buffer.clear();
    log.info("TMDB 수집 시작 페이지: {}", nextPage);
  }


  // 1. buffer가 비어있으면 채우고 buffer에 값이 있으면 꺼내고 제거
  @Override
  public @Nullable TmdbContentItem read() {
    if (buffer.isEmpty()) {
      fillBuffer();
    }
    return buffer.poll();
  }

  @Override
  public void update(ExecutionContext executionContext) {
    if (!buffer.isEmpty()) {
      throw new IllegalStateException(
          "chunk 경계와 TMDB 페이지 경계가 어긋났습니다. buffer 잔여=%d건, nextPage=%d"
          .formatted(buffer.size(), nextPage));
    }
    executionContext.putInt(getExecutionContextKey(CURSOR_KEY), nextPage);
  }

  // 1. buffer가 비어있거나 maxPages, totalPages가 nextPage보다 같거나 크면 반복
  private void fillBuffer() {
    while (buffer.isEmpty() && nextPage <= maxPages && nextPage <= totalPages) {
      TmdbPageResponse<? extends TmdbContentItem> response = fetchPage(nextPage);
      nextPage++;

      if (response == null) {
        continue;
      }
      if (response.totalPages() > 0) {
        totalPages = response.totalPages();
      }
      if (response.results() != null) {
        buffer.addAll(response.results());
      }
    }
  }

  private TmdbPageResponse<? extends TmdbContentItem> fetchPage(int page) {
    try {
      // fetcher를 실행해라
      return fetcher.apply(page);
    } catch (RetryableException e) {
      log.error("TMDB 재시도 소진: page={}", page, e);
      throw new ContentException(
          ContentErrorCode.TMDB_TEMPORARILY_UNAVAILABLE,
          Map.of("page", page));
    }
  }
}
