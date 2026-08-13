package com.codeit.mople.domain.content.client.tmdb.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.mople.domain.content.client.tmdb.dto.TmdbMovieResponse;
import com.codeit.mople.domain.content.client.tmdb.dto.TmdbPageResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;

@DisplayName("TmdbPageItemReader 단위 테스트")
public class TmdbPageItemReaderTest {

  private static final int PAGE_SIZE = TmdbPageItemReader.PAGE_SIZE;

  private final List<Integer> requestedPages = new ArrayList<>();

  @Nested
  @DisplayName("페이지 순회")
  class Paging{

    @Test
    @DisplayName("update가 저장한 커서를 open이 복원해 다음 페이지부터 읽는지 확인")
    void open_RestoresCursorSavedByUpdate() {
      ExecutionContext context = new ExecutionContext();
      TmdbPageItemReader reader = reader(5, 5, PAGE_SIZE);
      reader.open(context);
      read(reader, PAGE_SIZE);
      reader.update(context);

      TmdbPageItemReader restarted = reader(5, 5, PAGE_SIZE);
      restarted.open(context);
      restarted.read();

      assertThat(requestedPages).containsExactly(2);
    }

    @Test
    @DisplayName("maxPages를 지정한 만큼 데이터를 가져오는가 확인")
    void read_StopsAtMaxPages() {
      TmdbPageItemReader reader = reader(2, 10, PAGE_SIZE);
      reader.open(new ExecutionContext());

      int count = readUntilNull(reader);

      assertThat(count).isEqualTo(PAGE_SIZE * 2);
      assertThat(requestedPages).containsExactly(1, 2);
    }

    @Test
    @DisplayName("Tmdb가 알려준 totalPages를 넘어서 요청하지 않음")
    void read_StopsAtTotalPages() {
      TmdbPageItemReader reader = reader(10, 2, PAGE_SIZE);
      reader.open(new ExecutionContext());

      int count = readUntilNull(reader);

      assertThat(count).isEqualTo(PAGE_SIZE * 2);
      assertThat(requestedPages).containsExactly(1, 2);
    }
  }

  @Nested
  @DisplayName("chunk 경계 가드")
  class ChunkBoundary {

    @Test
    @DisplayName("페이지 크기가 chunk 크기와 다르면 유실 대신 update에서 실패하는지")
    void update_PagesSizeDiffers_Throws() {
      ExecutionContext context = new ExecutionContext();
      TmdbPageItemReader reader = reader(5, 5, 15);
      reader.open(context);
      read(reader, PAGE_SIZE);

      assertThatThrownBy(() -> reader.update(context))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("chunk 경계와 TMDB 페이지 경계");
    }
  }

  // 가짜 reader
  private TmdbPageItemReader reader(int maxPages, int totalPages, int itemsPerPage) {
    requestedPages.clear();
    return new TmdbPageItemReader("testReader", page -> {
      requestedPages.add(page);
      return new TmdbPageResponse<TmdbMovieResponse>(
          page, items(page, itemsPerPage), totalPages, totalPages * itemsPerPage);
    }, maxPages);
  }

  // 가짜 데이터
  private List<TmdbMovieResponse> items(int page, int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(i -> new TmdbMovieResponse(
            (long) (page * 100 + i), "영화" + i, "줄거리", "/poster.jpg", List.of()))
        .toList();
  }

  // reader를 받아서 count만큼 돌림
  private void read(TmdbPageItemReader reader, int count) {
    for (int i = 0; i < count; i++) {
      reader.read();
    }
  }

  // reader에 몇 건 있는지 세어봄
  private int readUntilNull(TmdbPageItemReader reader) {
    int count = 0;
    while (reader.read() != null) {
      count++;
    }
    return count;
  }

}
