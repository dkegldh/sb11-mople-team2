package com.codeit.mople.domain.content.client.tmdb.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.repository.ContentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

@ExtendWith(MockitoExtension.class)
@DisplayName("TmdbContentItemWriter 단위 테스트")
public class TmdbContentItemWriterTest {

  private static final ContentType TYPE = ContentType.MOVIE;
  private static final String EXTERNAL_ID = "1";

  @Mock
  private ContentRepository contentRepository;

  @Captor
  private ArgumentCaptor<List<Content>> savedCaptor;

  private TmdbContentItemWriter writer;

  @BeforeEach
  void setUp() {
    writer = new TmdbContentItemWriter(contentRepository, TYPE, new SimpleMeterRegistry());
  }

  @Nested
  @DisplayName("기존 행 갱신")
  class Update {

    @Test
    @DisplayName("장르 캐시가 빈 날 tags는 기존 tags를 지우지 않음")
    void write_EmptyTags_KeepsStoredTags() throws Exception {
      Content stored = content("김부장", "줄거리", "/kim.jpg", List.of("액션"));
      givenStored(stored);

      writer.write(chunk(content("김부장", "줄거리", "/kim.jpg", List.of())));

      assertThat(stored.getTags()).containsExactly("액션");
    }

    @Test
    @DisplayName("tags가 빈 채 저장된 행은 다음 수집이 채움")
    void write_StoredTagsEmpty_FillsTags() throws Exception {
      Content stored = content("김부장", "줄거리", "/kim.jpg", List.of());
      givenStored(stored);

      writer.write(chunk(content("김부장", "줄거리", "/kim.jpg", List.of("액션"))));

      assertThat(stored.getTags()).containsExactly("액션");
    }

    @Test
    @DisplayName("TMDB가 빈 줄거리를 줘도 기존 description을 지우지 않음")
    void write_BlankDescription_KeepsStoredDescription() throws Exception {
      Content stored = content("김부장", "줄거리", "/kim.jpg", List.of("액션"));
      givenStored(stored);

      writer.write(chunk(content("김부장", "", "/kim.jpg", List.of("액션"))));

      assertThat(stored.getDescription()).isEqualTo("줄거리");
    }
  }

  @Nested
  @DisplayName("신규 저장")
  class Insert {

    @Test
    @DisplayName("같은 청크에 같은 externalId가 두 번 오면 한 건만 저장함")
    void write_DuplicateInChunk_SavesOnce() throws Exception {
      given(contentRepository.findByTypeAndExternalIdIn(TYPE, List.of(EXTERNAL_ID)))
          .willReturn(List.of());

      writer.write(chunk(
          content("김부장", "줄거리", "/kim.jpg", List.of("액션")),
          content("김부장 2차 수집", "줄거리", "kime.jpg", List.of("액션"))));

      verify(contentRepository).saveAll(savedCaptor.capture());
      assertThat(savedCaptor.getValue()).hasSize(1);
    }
  }

  private void givenStored(Content stored) {
    given(contentRepository.findByTypeAndExternalIdIn(TYPE, List.of(EXTERNAL_ID)))
        .willReturn(List.of(stored));
  }

  private Chunk<Content> chunk(Content... items) {
    return new Chunk<>(List.of(items));
  }

  private Content content(String title, String description, String thumbnailUrl,
      List<String> tags) {
    return new Content(TYPE, title, description, thumbnailUrl, tags, EXTERNAL_ID);
  }
}