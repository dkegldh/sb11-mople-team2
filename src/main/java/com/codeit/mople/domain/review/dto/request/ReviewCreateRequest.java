package com.codeit.mople.domain.review.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReviewCreateRequest(
    @NotNull(message = "콘텐츠 ID는 필수입니다.")
    UUID contentId,

    @NotBlank(message = "리뷰 내용을 작성해주세요.")
    @Size(max = 500, message = "리뷰 내용은 500자 이하여야 합니다.")
    String text,

    @DecimalMin(value = "1.0", message = "별점은 1점 이상이어야 합니다.")
    @DecimalMax(value = "5.0", message = "별점은 5점 이하여야 합니다.")
    @NotNull(message = "별점을 선택해주세요.")
    Double rating
) {

}
