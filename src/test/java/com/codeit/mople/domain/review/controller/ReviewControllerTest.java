package com.codeit.mople.domain.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.auth.repository.AccountLockRepository;
import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.auth.security.CustomOAuth2UserService;
import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.auth.security.handler.OAuth2FailureHandler;
import com.codeit.mople.domain.auth.security.handler.OAuth2SuccessHandler;
import com.codeit.mople.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition;
import com.codeit.mople.domain.review.dto.request.ReviewQueryCondition.ReviewSortBy;
import com.codeit.mople.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.mople.domain.review.dto.response.ReviewCursorResponse;
import com.codeit.mople.domain.review.dto.response.ReviewResponse;
import com.codeit.mople.domain.review.exception.ReviewErrorCode;
import com.codeit.mople.domain.review.exception.ReviewException;
import com.codeit.mople.domain.review.service.ReviewService;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.global.config.SecurityConfig;
import com.codeit.mople.global.dto.SortDirection;
import com.codeit.mople.global.dto.UserSummary;
import com.codeit.mople.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(SecurityConfig.class)
@WebMvcTest(ReviewController.class)
public class ReviewControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private JwtProvider jwtProvider;

  @MockitoBean
  private CustomOAuth2UserService customOAuth2UserService;

  @MockitoBean
  private OAuth2SuccessHandler oAuth2SuccessHandler;

  @MockitoBean
  private OAuth2FailureHandler oAuth2FailureHandler;


  @MockitoBean
  AccountLockRepository accountLockRepository;

  @MockitoBean
  private ReviewService reviewService;

  @MockitoBean
  private SessionTokenRepository sessionTokenRepository;

  private CustomUserDetails userDetails;
  private UUID authorId;
  private UUID contentId;
  private UUID reviewId;
  private String reviewText;
  private Double reviewRating;
  private ReviewCreateRequest createRequest;

  private String newText;
  private Double newRating;
  private ReviewUpdateRequest updateRequest;

  @BeforeEach
  void setUp() {
    authorId = UUID.randomUUID();
    userDetails = new CustomUserDetails(authorId, Role.USER);

    contentId = UUID.randomUUID();
    reviewId = UUID.randomUUID();

    reviewText = "리뷰 내용";
    reviewRating = 5.0;
    createRequest = new ReviewCreateRequest(contentId, reviewText, reviewRating);

    newText = "수정한 내용";
    newRating = 3.0;
    updateRequest = new ReviewUpdateRequest(newText, newRating);
  }

  @Nested
  @DisplayName("리뷰 생성")
  class Create {

    @Test
    @DisplayName("리뷰 생성 성공")
    void create_success() throws Exception {
      // given

      // BeforeEach에서 authorId, contentId, reviewId, Review Create Request 초기화

      ReviewResponse response = new ReviewResponse(
          reviewId,
          contentId,
          new UserSummary(
              authorId,
              "test",
              null
          ),
          reviewText,
          reviewRating
      );

      given(reviewService.create(eq(authorId), any(ReviewCreateRequest.class)))
          .willReturn(response);

      // when & then
      mockMvc.perform(post("/api/reviews")
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(createRequest))
          )
          .andExpect(status().isCreated())
          .andExpect(header().string("Location", "/api/reviews/" + reviewId.toString()))
          .andExpect(jsonPath("$.id").value(reviewId.toString()))
          .andExpect(jsonPath("$.contentId").value(contentId.toString()))
          .andExpect(jsonPath("$.author.userId").value(authorId.toString()))
          .andExpect(jsonPath("$.author.name").value("test"))
          .andExpect(jsonPath("$.author.profileImageUrl").isEmpty())
          .andExpect(jsonPath("$.text").value(reviewText))
          .andExpect(jsonPath("$.rating").value(reviewRating)
          );

      verify(reviewService).create(eq(authorId), any(ReviewCreateRequest.class));
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 콘텐츠ID가 없음(400 에러)")
    void create_fail_nullContentId() throws Exception {
      // given
      ReviewCreateRequest invalidRequest = new ReviewCreateRequest(null, reviewText, reviewRating);

      // when & then
      mockMvc.perform(post("/api/reviews")
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalidRequest))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 리뷰 내용이 비어있음(400 에러)")
    void create_fail_blankText() throws Exception {
      // given
      ReviewCreateRequest invalidRequest = new ReviewCreateRequest(contentId, "", reviewRating);

      // when & then
      mockMvc.perform(post("/api/reviews")
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalidRequest))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 리뷰 내용 길이가 500 초과(400 에러)")
    void create_fail_textGreaterThanMax() throws Exception {
      // given
      // 501자 길이의 리뷰 내용
      String text = "a".repeat(501);
      ReviewCreateRequest invalidRequest = new ReviewCreateRequest(contentId, text, reviewRating);

      // when & then
      mockMvc.perform(post("/api/reviews")
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalidRequest))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 별점이 없음(400 에러)")
    void create_fail_nullRating() throws Exception {
      // given
      ReviewCreateRequest invalidRequest = new ReviewCreateRequest(contentId, reviewText, null);

      // when & then
      mockMvc.perform(post("/api/reviews")
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalidRequest))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 별점 범위 1점 미만(400 에러)")
    void create_fail_underMinRating() throws Exception {
      // given
      ReviewCreateRequest invalidRequest = new ReviewCreateRequest(contentId, reviewText, 0.0);

      // when & then
      mockMvc.perform(post("/api/reviews")
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalidRequest))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 별점 범위 5점 초과(400 에러)")
    void create_fail_overMaxRating() throws Exception {
      // given
      ReviewCreateRequest invalidRequest = new ReviewCreateRequest(contentId, reviewText, 6.0);

      // when & then
      mockMvc.perform(post("/api/reviews")
              .with(user(userDetails))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalidRequest))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

  }

  @Nested
  @DisplayName("리뷰 목록 조회")
  class FindAll {

    @Test
    @DisplayName("리뷰 목록 조회 성공")
    void findAll_success() throws Exception {
      // given

      // BeforeEach에서 reviewId, contentId, authorId, reviewText, reviewRating, userDetails를 초기화

      ReviewResponse reviewResponse = new ReviewResponse(
          reviewId,
          contentId,
          new UserSummary(
              authorId,
              "test",
              null
          ),
          reviewText,
          reviewRating
      );

      ReviewCursorResponse response = new ReviewCursorResponse(
          List.of(reviewResponse),
          null,
          null,
          false,
          1L,
          ReviewSortBy.RATING,
          SortDirection.DESCENDING
      );

      given(reviewService.findAll(any(ReviewQueryCondition.class)))
          .willReturn(response);

      // when & then
      mockMvc.perform(get("/api/reviews")
              .param("limit", "10")
              .param("sortDirection", "DESCENDING")
              .param("sortBy", "rating")
              .with(user(userDetails))
          )
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").isArray())
          .andExpect(jsonPath("$.hasNext").value(false))
          .andExpect(jsonPath("$.totalCount").value(1L))
          .andExpect(jsonPath("$.sortBy").value("rating"))
          .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));

      verify(reviewService).findAll(any(ReviewQueryCondition.class));
    }

    @Test
    @DisplayName("리뷰 목록 조회 실패 - limit 누락(400 에러)")
    void findAll_fail_withoutLimit() throws Exception {
      // given

      // BeforeEach에서 userDetails를 초기화

      // when & then
      mockMvc.perform(get("/api/reviews")
              .param("sortDirection", "DESCENDING")
              .param("sortBy", "rating")
              .with(user(userDetails))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 목록 조회 실패 - 정렬 조건 누락(400 에러)")
    void findAll_fail_withoutSortBy() throws Exception {
      // given

      // BeforeEach에서 userDetails를 초기화

      // when & then
      mockMvc.perform(get("/api/reviews")
              .param("limit", "10")
              .param("sortDirection", "DESCENDING")
              .with(user(userDetails))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 목록 조회 실패 - 정렬 방향 누락(400 에러)")
    void findAll_fail_withoutSortDirection() throws Exception {
      // given

      // BeforeEach에서 userDetails를 초기화

      // when & then
      mockMvc.perform(get("/api/reviews")
              .param("limit", "10")
              .param("sortBy", "rating")
              .with(user(userDetails))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 목록 조회 실패 - limit 값 1 미만(400 에러)")
    void findAll_fail_limitLessThanMin() throws Exception {
      // given

      // BeforeEach에서 userDetails를 초기화

      // when & then
      mockMvc.perform(get("/api/reviews")
              .param("limit", "0")
              .param("sortDirection", "DESCENDING")
              .param("sortBy", "rating")
              .with(user(userDetails))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 목록 조회 실패 - limit 값 100 초과(400 에러)")
    void findAll_fail_limitGreaterThanMax() throws Exception {
      // given

      // BeforeEach에서 userDetails를 초기화

      // when & then
      mockMvc.perform(get("/api/reviews")
              .param("limit", "101")
              .param("sortDirection", "DESCENDING")
              .param("sortBy", "rating")
              .with(user(userDetails))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 목록 조회 실패 - 잘못된 limit 값(400 에러)")
    void findAll_fail_invalidLimit() throws Exception {
      // given

      // BeforeEach에서 userDetails를 초기화

      // when & then
      mockMvc.perform(get("/api/reviews")
              .param("limit", "ANY")
              .param("sortDirection", "DESCENDING")
              .param("sortBy", "rating")
              .with(user(userDetails))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 목록 조회 실패 - 잘못된 정렬 조건(400 에러)")
    void findAll_fail_invalidSortBy() throws Exception {
      // given

      // BeforeEach에서 userDetails를 초기화

      // when & then
      mockMvc.perform(get("/api/reviews")
              .param("limit", "10")
              .param("sortDirection", "DESCENDING")
              .param("sortBy", "ANY")
              .with(user(userDetails))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 목록 조회 실패 - 잘못된 정렬 방향(400 에러)")
    void findAll_fail_invalidSortDirection() throws Exception {
      // given

      // BeforeEach에서 userDetails를 초기화

      // when & then
      mockMvc.perform(get("/api/reviews")
              .param("limit", "10")
              .param("sortDirection", "ANY")
              .param("sortBy", "rating")
              .with(user(userDetails))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

  }

  @Nested
  @DisplayName("리뷰 수정")
  class Update {

    @Test
    @DisplayName("리뷰 수정 성공")
    void update_success() throws Exception {
      // given

      // BeforeEach에서 newText, newRating, updateRequest를 초기화

      ReviewResponse response = new ReviewResponse(
          reviewId,
          contentId,
          new UserSummary(
              authorId,
              "test",
              null
          ),
          newText,
          newRating
      );

      given(reviewService.update(eq(reviewId), any(ReviewUpdateRequest.class), eq(authorId)))
          .willReturn(response);

      // when & then
      mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(updateRequest))
              .with(user(userDetails))
              .with(csrf()))
          .andExpect(status().isOk())
          .andDo(print())
          .andExpect(jsonPath("$.id").value(reviewId.toString()))
          .andExpect(jsonPath("$.contentId").value(contentId.toString()))
          .andExpect(jsonPath("$.author.userId").value(authorId.toString()))
          .andExpect(jsonPath("$.author.name").value("test"))
          .andExpect(jsonPath("$.author.profileImageUrl").isEmpty())
          .andExpect(jsonPath("$.text").value(newText))
          .andExpect(jsonPath("$.rating").value(newRating));

      verify(reviewService).update(eq(reviewId), any(ReviewUpdateRequest.class), eq(authorId));
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 리뷰 내용이 공백(400 에러)")
    void update_fail_invalidText() throws Exception {
      // given
      ReviewUpdateRequest invalidRequest = new ReviewUpdateRequest(" ", newRating);

      // BeforeEach에서 reviewId, userDetails 초기화

      // when & then
      mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalidRequest))
              .with(user(userDetails))
              .with(csrf()))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 리뷰 내용 길이가 500 초과(400 에러)")
    void update_fail_textGraterThanMax() throws Exception {
      // given
      // 501자 길이의 리뷰 내용
      String text = "a".repeat(501);
      ReviewUpdateRequest invalidRequest = new ReviewUpdateRequest(text, reviewRating);

      // when & then
      mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
              .with(user(userDetails))
              .with(csrf())
              .param("authorId", authorId.toString())
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalidRequest))
          )
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 별점이 최소 범위 미만(1점 미만, 400 에러)")
    void update_fail_underRating() throws Exception {
      // given
      ReviewUpdateRequest invalidRequest = new ReviewUpdateRequest(newText, 0.0);

      // BeforeEach에서 reviewId, userDetails 초기화

      // when & then
      mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalidRequest))
              .with(user(userDetails))
              .with(csrf()))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 별점이 최대 범위 초과(5점 초과, 400 에러)")
    void update_fail_overRating() throws Exception {
      // given
      ReviewUpdateRequest invalidRequest = new ReviewUpdateRequest(newText, 6.0);

      // BeforeEach에서 reviewId, userDetails 초기화

      // when & then
      mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalidRequest))
              .with(user(userDetails))
              .with(csrf()))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 수정할 리뷰 내용과 별점 모두 없음(400 에러)")
    void update_fail_noUpdateField() throws Exception {
      // given
      ReviewUpdateRequest invalidRequest = new ReviewUpdateRequest(null, null);

      // BeforeEach에서 reviewId, userDetails 초기화

      // when & then
      mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(invalidRequest))
              .with(user(userDetails))
              .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 리뷰 작성자가 아님(403 에러)")
    void update_fail_forbidden() throws Exception {
      // given

      // BeforeEach에서 authorid, reviewId, userDetails 초기화

      given(reviewService.update(eq(reviewId), any(ReviewUpdateRequest.class), eq(authorId)))
          .willThrow(new ReviewException(ReviewErrorCode.REVIEW_FORBIDDEN));

      // when & then
      mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(updateRequest))
              .with(user(userDetails))
              .with(csrf()))
          .andExpect(status().isForbidden());

      verify(reviewService).update(eq(reviewId), any(ReviewUpdateRequest.class), eq(authorId));
    }

  }

  @Nested
  @DisplayName("리뷰 삭제")
  class Delete {

    @Test
    @DisplayName("리뷰 삭제 성공")
    void delete_success() throws Exception {
      // given

      // BeforeEach에서 reviewId, userDetails 초기화

      doNothing().when(reviewService).delete(reviewId, authorId);

      // when & then
      mockMvc.perform(delete("/api/reviews/{reviewId}", reviewId)
              .with(user(userDetails))
              .with(csrf()))
          .andExpect(status().isNoContent());

      verify(reviewService).delete(reviewId, authorId);
    }

    @Test
    @DisplayName("리뷰 삭제 실패 - 리뷰 작성자가 아님(403 에러)")
    void delete_fail_forbidden() throws Exception {
      // given

      doThrow(new ReviewException(ReviewErrorCode.REVIEW_FORBIDDEN))
          .when(reviewService).delete(reviewId, authorId);

      // when & then
      mockMvc.perform(delete("/api/reviews/{reviewId}", reviewId)
              .with(user(userDetails))
              .with(csrf()))
          .andExpect(status().isForbidden());

      verify(reviewService).delete(reviewId, authorId);
    }

  }

}
