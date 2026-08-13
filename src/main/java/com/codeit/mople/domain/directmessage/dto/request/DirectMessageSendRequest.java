package com.codeit.mople.domain.directmessage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DirectMessageSendRequest(
    @NotBlank(message = "메시지 내용은 비어있을 수 없습니다.")
    @Size(max = 1000, message = "메시지는 최대 1000자까지 전송 가능합니다.")
    String content
) {

}
