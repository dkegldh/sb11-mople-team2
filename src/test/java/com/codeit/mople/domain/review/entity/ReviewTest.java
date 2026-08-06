package com.codeit.mople.domain.review.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.user.entity.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class ReviewTest {

  private User author;
  private Content content;

  private String text;
  private double rating;

  private Review review;
  private String newText;
  private double newRating;

  @BeforeEach
  void setUp() {
    author = User.createUser("test@test.com", "12345678", "test");
    content = new Content(
        ContentType.DRAMA,
        "test",
        "test 콘텐츠",
        "test/image.png",
        List.of("테스트")
    );

    text = "리뷰 내용";
    rating = 5.0;
  }

  @Nested
  @DisplayName("리뷰 생성")
  class Create {

    @Test
    @DisplayName("리뷰 생성 성공")
    void create_success() {
      // given

      // BeforeEach에서 user, author, content, text, rating을 초기화

      // when
      Review review = Review.create(content, author, text, rating);

      // then
      assertThat(review.getContent()).isEqualTo(content);
      assertThat(review.getAuthor()).isEqualTo(author);
      assertThat(review.getText()).isEqualTo(text);
      assertThat(review.getRating()).isEqualTo(rating);

    }

  }

  @Nested
  @DisplayName("리뷰 수정")
  class Update {

    @BeforeEach
    void setUp() {
      review = Review.create(content, author, text, rating);

      newText = "수정한 내용";
      newRating = 3.0;
    }

    @Test
    @DisplayName("리뷰 수정 성공 - 내용, 평점 둘 다 수정")
    void update_success() {
      // given

      // BeforeEach에서 user, author, content, text, rating을 통해 review와 newText, newRating을 초기화

      // when
      review.updateText(newText);
      review.updateRating(newRating);

      // then
      assertThat(review.getText()).isEqualTo(newText);
      assertThat(review.getRating()).isEqualTo(newRating);
    }

    @Test
    @DisplayName("리뷰 수정 성공 - 내용만 수정")
    void update_success_onlyText() {
      // given

      // BeforeEach에서 user, author, content, text, rating을 통해 review와 newText을 초기화

      // when
      review.updateText(newText);

      // then
      assertThat(review.getText()).isEqualTo(newText);
      assertThat(review.getRating()).isEqualTo(rating);
    }

    @Test
    @DisplayName("리뷰 수정 성공 - 평점만 수정")
    void update_success_onlyRating() {
      // given

      // BeforeEach에서 user, author, content, text, rating을 통해 review와 newRating을 초기화

      // when
      review.updateRating(newRating);

      // then
      assertThat(review.getText()).isEqualTo(text);
      assertThat(review.getRating()).isEqualTo(newRating);
    }

  }

}
