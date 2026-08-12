package com.codeit.mople.domain.content.client.tmdb.batch;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.repository.ContentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

@Slf4j
public class TmdbContentItemWriter implements ItemWriter<Content> {

  private final ContentRepository contentRepository;
  private final ContentType contentType;
  private final Counter duplicateCounter;

  public TmdbContentItemWriter(
      ContentRepository contentRepository,
      ContentType contentType,
      MeterRegistry meterRegistry) {
    this.contentRepository = contentRepository;
    this.contentType = contentType;
    this.duplicateCounter = Counter.builder("batch.tmdb.duplicate")
        .description("TMDB 수집 배치 중복 건수(DB 기존 + 청크 내)")
        .tag("type", contentType.name())
        .register(meterRegistry);
  }

  @Override
  public void write(@NonNull Chunk<? extends Content> chunk) throws Exception {
    // Content를 List로 할당
    List<? extends Content> items = chunk.getItems();
    if (items.isEmpty()) {
      return;
    }

    List<String> externalIds = items.stream().map(Content::getExternalId).toList();

    Set<String> existingIds = Set.copyOf(
        contentRepository.findExternalIdsByTypeAndExternalIdIn(contentType, externalIds));

    Map<String, Content> newContents = new LinkedHashMap<>();
    for (Content content : items) {
      if (existingIds.contains(content.getExternalId())) {
        continue;
      }
      newContents.putIfAbsent(content.getExternalId(), content);
    }

    int duplicateCount = items.size() - newContents.size();
    if (duplicateCount > 0) {
      duplicateCounter.increment(duplicateCount);
    }

    if (newContents.isEmpty()) {
      log.info("TMDB 수집({}) 읽음={}, 신규={}, 중복={}", contentType, items.size(), 0, duplicateCount);
      return;
    }

    List<Content> saved = contentRepository.saveAll(newContents.values());
    log.info("TMDB 수집 ({}) 읽음={}, 신규={}, 중복={}", contentType, items.size(), saved.size(), duplicateCount);
  }
}
