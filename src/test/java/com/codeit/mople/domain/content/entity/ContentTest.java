package com.codeit.mople.domain.content.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class ContentTest {

  private Content content;

  @BeforeEach
  void setUp() {
    content = new Content(
        ContentType.TV_SERIES,
        "test",
        "test 콘텐츠",
        "test/image.png",
        List.of("테스트")
    );
  }

  @Nested
  @DisplayName("평균 별점 계산")
  class calculateAverageRating {

    @Test
    @DisplayName("평균 별점 계산 성공")
    void calculateAverageRating_success() {
      // given

      // BeforeEach에서 content를 초기화
      
      // content에 대한 리뷰 총점을 9.0, 리뷰 개수를 2개로 가정
      ReflectionTestUtils.setField(content, "ratingSum", 9.0);
      ReflectionTestUtils.setField(content, "reviewCount", 2);

      // when
      double result = content.calculateAverageRating();

      // then
      assertThat(result).isEqualTo(4.5);
    }

    @Test
    @DisplayName("평균 별점 계산 성공 - 리뷰 없음")
    void calculateAverageRating_success_empty_review() {
      // given

      // BeforeEach에서 content를 초기화

      // when
      double result = content.calculateAverageRating();

      // then
      assertThat(result).isZero();
    }

  }

  @Nested
  @DisplayName("콘텐츠 업데이트 및 동기화 방어 로직")
  class ContentUpdateAndSync {

    @Test
    @DisplayName("updateContentInfo 성공 - null 값이 들어오면 기존 데이터를 유지한다")
    void updateContentInfo_IgnoresNullValues() {
      Content testContent = new Content(ContentType.MOVIE, "기존 제목", "기존 설명", "old.png", List.of("태그1"));

      //모두 null 전달
      testContent.updateContentInfo(null, null, null, null);

      //null 덮어쓰기가 방어되어 기존 값이 유지됨
      assertThat(testContent.getTitle()).isEqualTo("기존 제목");
      assertThat(testContent.getDescription()).isEqualTo("기존 설명");
      assertThat(testContent.getThumbnailUrl()).isEqualTo("old.png");
      assertThat(testContent.getTags()).containsExactly("태그1");
    }

    @Test
    @DisplayName("syncFromExternal 성공 - 기존 데이터가 비어있을 때만 응답 데이터를 채워 넣는다")
    void syncFromExternal_FillsOnlyEmptyFields() {
      //제목은 기존에 존재하고, 설명/썸네일/태그는 비어있는 상태
      Content testContent = new Content(ContentType.MOVIE, "기존 제목", "", null, List.of());

      //배치 등에서 새로운 외부 데이터 동기화 시도
      boolean isChanged = testContent.syncFromExternal(
          "새로운 제목", "새로운 설명", "new.png", List.of("새로운 태그")
      );

      assertThat(isChanged).isTrue();

      //제목은 비어있지 않았으므로 덮어써지지 않고 유지됨
      assertThat(testContent.getTitle()).isEqualTo("기존 제목");

      //설명, 썸네일, 태그는 비어있었으므로 새로운 데이터로 채워짐
      assertThat(testContent.getDescription()).isEqualTo("새로운 설명");
      assertThat(testContent.getThumbnailUrl()).isEqualTo("new.png");
      assertThat(testContent.getTags()).containsExactly("새로운 태그");
    }
  }

}
