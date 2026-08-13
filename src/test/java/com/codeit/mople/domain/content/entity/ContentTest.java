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

}
