package com.codeit.mople.domain.content.client.sportsdb.batch;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.repository.ContentRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

//변환된 엔티티를 DB에 일괄 저장
@Slf4j
@Component
@RequiredArgsConstructor
public class SportsDbItemWriter implements ItemWriter<Content> {

  private final ContentRepository contentRepository;

  @Override
  public void write(Chunk<? extends Content> chunk) {
    log.info("Content DB 저장 시작 - Chunk 사이즈: {}건", chunk.getItems().size());

    //c로 캐스팅하거나 map을 통해 Content 타입으로 명시
    List<Content> items = chunk.getItems().stream()
        .map(c -> (Content) c)
        .toList();

    //이번 Chunk의 외부 식별자 추출
    List<String> externalIds = items.stream()
        .map(Content::getExternalId)
        .toList();

    //DB에 이미 존재하는 식별자 조회
    List<String> existingIds = contentRepository
        .findExternalIdsByTypeAndExternalIdIn(ContentType.SPORT, externalIds);

    //기존 DB에 없는 새로운 데이터만 필터링
    Set<String> knownExternalIds = new HashSet<>(existingIds);
    List<Content> newContents = items.stream()
        .filter(content -> knownExternalIds.add(content.getExternalId()))
        .toList();

    //새로운 데이터만 저장
    if (!newContents.isEmpty()) {
      contentRepository.saveAll(newContents);
      log.info("새로운 경기 데이터 {}건 저장 완료 (중복 {}건 스킵)",
          newContents.size(), items.size() - newContents.size());
    } else {
      log.info("저장할 새로운 경기 데이터가 없습니다. (모두 중복)");
    }
  }
}