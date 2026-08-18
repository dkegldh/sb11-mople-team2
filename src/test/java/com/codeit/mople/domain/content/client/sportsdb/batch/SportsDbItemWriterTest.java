package com.codeit.mople.domain.content.client.sportsdb.batch;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.repository.ContentRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

@ExtendWith(MockitoExtension.class)
class SportsDbItemWriterTest {

  @Mock
  private ContentRepository contentRepository;

  @InjectMocks
  private SportsDbItemWriter writer;

  @Test
  @DisplayName("모두 새로운 데이터인 경우 전부 저장(saveAll)된다")
  void write_Success_AllNewContents() throws Exception {
    Content content1 = mock(Content.class);
    given(content1.getExternalId()).willReturn("EXT-1");
    Content content2 = mock(Content.class);
    given(content2.getExternalId()).willReturn("EXT-2");

    Chunk<Content> chunk = new Chunk<>(List.of(content1, content2));

    //DB에 존재하는 외부 ID가 없다고 모킹
    given(contentRepository.findExternalIdsByTypeAndExternalIdIn(eq(ContentType.SPORT), anyList()))
        .willReturn(List.of());

    writer.write(chunk);

    //두 객체 모두 포함된 리스트로 saveAll이 호출되었는지 검증
    verify(contentRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("일부 데이터가 중복인 경우 새로운 데이터만 필터링되어 저장된다")
  void write_Success_PartialDuplication() throws Exception {
    Content existingContent = mock(Content.class);
    given(existingContent.getExternalId()).willReturn("EXT-OLD");

    Content newContent = mock(Content.class);
    given(newContent.getExternalId()).willReturn("EXT-NEW");

    Chunk<Content> chunk = new Chunk<>(List.of(existingContent, newContent));

    //DB에 "EXT-OLD"가 이미 존재한다고 모킹
    given(contentRepository.findExternalIdsByTypeAndExternalIdIn(eq(ContentType.SPORT), anyList()))
        .willReturn(List.of("EXT-OLD"));

    writer.write(chunk);

    //saveAll이 호출되었고, 그 안에는 "EXT-NEW"를 가진 newContent만 전달되어야 함
    verify(contentRepository).saveAll(org.mockito.ArgumentMatchers.argThat(iterable -> {
      List<Content> savedList = (List<Content>) iterable; //Iterable을 List로 캐스팅
      return savedList.size() == 1 && savedList.contains(newContent);
    }));
  }

  @Test
  @DisplayName("모든 데이터가 중복인 경우 saveAll이 호출되지 않는다")
  void write_Ignored_AllDuplicated() throws Exception {
    Content existingContent = mock(Content.class);
    given(existingContent.getExternalId()).willReturn("EXT-OLD");

    Chunk<Content> chunk = new Chunk<>(List.of(existingContent));

    given(contentRepository.findExternalIdsByTypeAndExternalIdIn(eq(ContentType.SPORT), anyList()))
        .willReturn(List.of("EXT-OLD"));

    writer.write(chunk);

    verify(contentRepository, never()).saveAll(anyList());
  }
}