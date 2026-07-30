package com.codeit.mople.domain.content.dto;

import jakarta.validation.constraints.Pattern;
import java.util.List;

public record ContentUpdateRequest(
    @Pattern(regexp = "^(?!\\p{javaWhitespace}*$).+", message = "제목은 공백일 수 없습니다.")    String title,
    String description,
    List<String> tags
) {

}
