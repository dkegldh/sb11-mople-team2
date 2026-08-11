package com.codeit.mople.domain.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public record UserUpdateRequest(

    @Pattern(
        regexp = "^(?!\\p{javaWhitespace}*$).+",
        message = "이름을 입력해주세요."
    )
    @Size(min = 1, max = 20, message = "이름은 최소 1자, 최대 20자여야 합니다.")
    String name
) {

}