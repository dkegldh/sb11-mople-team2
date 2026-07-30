package com.codeit.mople.domain.content.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ContentCreateRequest(
    @NotBlank(message = "콘텐츠 타입은 필수입니다.")
    String type,

    @NotBlank(message = "제목은 필수입니다.")
    String title,

    String description,

    List<String> tags
) {

}
