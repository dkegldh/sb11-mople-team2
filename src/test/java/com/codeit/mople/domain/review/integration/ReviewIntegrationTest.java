package com.codeit.mople.domain.review.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.mople.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.mople.domain.review.dto.response.ReviewResponse;
import com.codeit.mople.domain.review.entity.Review;
import com.codeit.mople.domain.review.repository.ReviewRepository;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.config.SecurityConfig;
import com.codeit.mople.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@Import(SecurityConfig.class)
@Transactional
@SpringBootTest
@AutoConfigureMockMvc
public class ReviewIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private JwtProvider jwtProvider;

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private ReviewRepository reviewRepository;

  private CustomUserDetails userDetails;
  private User savedAuthor;
  private Content savedContent;
  private ReviewCreateRequest createRequest;
  private String reviewText;
  private Double reviewRating;

  private ReviewUpdateRequest updateRequest;
  private String newText;
  private Double newRating;
  private Review review;
  private Review savedReview;

  @BeforeEach
  void setUp() {
    savedAuthor = userRepository.save(
        User.createUser("test@test.com", "12345678", "test")
    );
    userDetails = new CustomUserDetails(savedAuthor.getId(), Role.USER);

    savedContent = contentRepository.save(new Content(
            ContentType.TV_SERIES,
            "test",
            "test 콘텐츠",
            "test/image.png",
            List.of("테스트")
        )
    );

    reviewText = "리뷰 내용";
    reviewRating = 4.0;

    createRequest = new ReviewCreateRequest(savedContent.getId(), reviewText, reviewRating);

    newText = "수정한 내용";
    newRating = 3.0;
    updateRequest = new ReviewUpdateRequest(newText, newRating);
    review = Review.create(savedContent, savedAuthor, reviewText, reviewRating);
  }

  @Nested
  @DisplayName("리뷰 생성")
  class Create {

    @Test
    @DisplayName("리뷰 생성 성공")
    void create_success() throws Exception {
      // given

      // BeforeEach에서 savedAuthor, savedContent, request 초기화

      // when & then
      MvcResult result = mockMvc.perform(post("/api/reviews")
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(createRequest))
          )
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").isNotEmpty())
          .andExpect(jsonPath("$.contentId").value(savedContent.getId().toString()))
          .andExpect(jsonPath("$.author.userId").value(savedAuthor.getId().toString()))
          .andExpect(jsonPath("$.author.name").value(savedAuthor.getName()))
          .andExpect(jsonPath("$.author.profileImageUrl").value(savedAuthor.getProfileImageUrl()))
          .andExpect(jsonPath("$.text").value(reviewText))
          .andExpect(jsonPath("$.rating").value(reviewRating))
          .andReturn();

      // 응답 추출
      ReviewResponse response =
          objectMapper.readValue(result.getResponse().getContentAsString(), ReviewResponse.class);

      // 헤더 검증
      assertThat(result.getResponse().getHeader("Location"))
          .isEqualTo("/api/reviews/" + response.id());

      // DB 검증
      Review savedReview = reviewRepository.findById(response.id()).orElseThrow();

      assertThat(savedReview.getContent().getId()).isEqualTo(savedContent.getId());
      assertThat(savedReview.getAuthor().getId()).isEqualTo(savedAuthor.getId());
      assertThat(savedReview.getText()).isEqualTo(reviewText);
      assertThat(savedReview.getRating()).isEqualTo(reviewRating);
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 콘텐츠가 존재하지 않음(404 에러)")
    void create_fail_notFoundContent() throws Exception {
      // given
      UUID notExistContentId = UUID.randomUUID();

      ReviewCreateRequest invalidRequest =
          new ReviewCreateRequest(notExistContentId, reviewText, reviewRating);

      // when & then
      mockMvc.perform(post("/api/reviews")
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalidRequest))
          )
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 인증되지 않은 사용자(401 에러)")
    void create_fail_unauthorized() throws Exception {
      // given

      // BeforeEach에서 request 초기화

      // when & then
      mockMvc.perform(post("/api/reviews")
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(createRequest))
          )
          .andExpect(status().isUnauthorized());

      assertThat(reviewRepository.findAll()).isEmpty();
    }

  }

  @Nested
  @DisplayName("리뷰 목록 조회")
  class FindAll {

    @Test
    @DisplayName("리뷰 목록 조회 성공")
    void findAll_success() throws Exception {
      // given

      // BeforeEach에서 savedAuthor, savedContent 저장, review, userDetails를 초기화

      savedReview = reviewRepository.save(review);

      // when & then
      mockMvc.perform(get("/api/reviews")
              .param("limit", "10")
              .param("sortDirection", "ASCENDING")
              .param("sortBy", "rating")
              .with(user(userDetails))
          )
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").isArray())
          .andExpect(jsonPath("$.data.length()").value(1))
          .andExpect(jsonPath("$.data[0].id").value(savedReview.getId().toString()))
          .andExpect(jsonPath("$.data[0].contentId").value(savedContent.getId().toString()))
          .andExpect(jsonPath("$.data[0].author.userId").value(savedAuthor.getId().toString()))
          .andExpect(jsonPath("$.data[0].text").value(reviewText))
          .andExpect(jsonPath("$.data[0].rating").value(reviewRating))
          .andExpect(jsonPath("$.hasNext").value(false))
          .andExpect(jsonPath("$.totalCount").value(1L))
          .andExpect(jsonPath("$.sortBy").value("rating"))
          .andExpect(jsonPath("$.sortDirection").value("ASCENDING"));
    }

    @Test
    @DisplayName("리뷰 목록 조회 실패 - 인증되지 않은 사용자(401 에러)")
    void findAll_fail_unauthorized() throws Exception {
      // given

      // BeforeEach에서 userDetails를 초기화

      // when & then
      mockMvc.perform(get("/api/reviews")
              .param("limit", "10")
              .param("sortDirection", "ASCENDING")
              .param("sortBy", "rating")
          )
          .andExpect(status().isUnauthorized());
    }

  }

  @Nested
  @DisplayName("리뷰 수정")
  class Update {

    @Test
    @DisplayName("리뷰 수정 성공")
    void update_success() throws Exception {
      // given
      savedReview = reviewRepository.save(review);

      // 기존 리뷰의 별점 통계 설정
      contentRepository.increaseRating(savedContent.getId(), reviewRating);

      // BeforeEach에서 updateRequest, userDetails를 초기화

      // when & then
      mockMvc.perform(patch("/api/reviews/{reviewId}", savedReview.getId())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(updateRequest))
              .with(user(userDetails))
              .with(csrf())
          )
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(savedReview.getId().toString()))
          .andExpect(jsonPath("$.contentId").value(savedContent.getId().toString()))
          .andExpect(jsonPath("$.author.userId").value(savedAuthor.getId().toString()))
          .andExpect(jsonPath("$.author.name").value(savedAuthor.getName()))
          .andExpect(jsonPath("$.text").value(newText))
          .andExpect(jsonPath("$.rating").value(newRating));

      // Review 검증
      Review updatedReview = reviewRepository.findById(savedReview.getId()).orElseThrow();

      assertThat(updatedReview.getText()).isEqualTo(newText);
      assertThat(updatedReview.getRating()).isEqualTo(newRating);

      // Content 검증
      Content content = contentRepository.findById(savedContent.getId()).orElseThrow();

      assertThat(content.getReviewCount()).isEqualTo(1);
      assertThat(content.getRatingSum()).isEqualTo(newRating);
      assertThat(content.calculateAverageRating()).isEqualTo(newRating);
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 리뷰가 존재하지 않음(404 에러)")
    void update_fail_notFoundReview() throws Exception {
      // given
      UUID notExistReviewId = UUID.randomUUID();

      // BeforeEach에서 updateRequest, userDetails를 초기화

      // when & then
      mockMvc.perform(patch("/api/reviews/{reviewId}", notExistReviewId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(updateRequest))
              .with(user(userDetails))
              .with(csrf())
          )
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 인증되지 않은 사용자(401 에러)")
    void update_fail_unauthorized() throws Exception {
      // given
      savedReview = reviewRepository.save(review);

      // BeforeEach에서 updateRequest를 초기화

      // when & then
      mockMvc.perform(patch("/api/reviews/{reviewId}", savedReview.getId())
              .contentType(MediaType.APPLICATION_JSON)
              .with(csrf())
          )
          .andExpect(status().isUnauthorized());

      Review review = reviewRepository.findById(savedReview.getId()).orElseThrow();

      assertThat(review.getText()).isEqualTo(reviewText);
      assertThat(review.getRating()).isEqualTo(reviewRating);
    }

  }

  @Nested
  @DisplayName("리뷰 삭제")
  class Delete {

    @Test
    @DisplayName("리뷰 삭제 성공")
    void delete_success() throws Exception {
      // given
      savedReview = reviewRepository.save(review);

      // 리뷰가 존재하는 상태의 콘텐츠 통계 구성
      contentRepository.increaseRating(
          savedContent.getId(),
          review.getRating()
      );

      // BeforeEach에서 userDetails 초기화

      // when & then
      mockMvc.perform(delete("/api/reviews/{reviewId}", savedReview.getId())
              .with(user(userDetails))
              .with(csrf())
          )
          .andExpect(status().isNoContent());

      // DB에 리뷰 검증(행이 하나도 없어야 함)
      assertThat(reviewRepository.findById(savedReview.getId())).isEmpty();

      // 컨텐츠의 리뷰 개수와 평균 평점 검증
      Content content = contentRepository.findById(savedContent.getId()).orElseThrow();
      assertThat(content.getReviewCount()).isEqualTo(0); // == isZero()
      assertThat(content.calculateAverageRating()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("리뷰 삭제 실패 - 리뷰가 존재하지 않음(404 에러)")
    void delete_fail_notFoundReview() throws Exception {
      // given
      UUID notExistReviewId = UUID.randomUUID();

      // BeforeEach에서 userDetails 초기화

      // when & then
      mockMvc.perform(delete("/api/reviews/{reviewId}", notExistReviewId)
              .with(user(userDetails))
              .with(csrf())
          )
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("리뷰 삭제 실패 - 인증되지 않은 사용자(401 에러)")
    void delete_fail_unauthorized() throws Exception {
      // given
      savedReview = reviewRepository.save(review);

      // when & then
      mockMvc.perform(delete("/api/reviews/{reviewId}", savedReview.getId())
              .with(csrf())
          )
          .andExpect(status().isUnauthorized());

      assertThat(reviewRepository.findById(savedReview.getId())).isNotEmpty();
    }

  }

}
