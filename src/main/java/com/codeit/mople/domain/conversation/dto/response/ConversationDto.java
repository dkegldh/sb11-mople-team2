package com.codeit.mople.domain.conversation.dto.response;

import com.codeit.mople.domain.directmessage.dto.response.DirectMessageDto;
import com.codeit.mople.global.dto.UserSummary;
import java.util.UUID;

public record ConversationDto(
    UUID id,
    UserSummary with,
    DirectMessageDto lastestMessage,
    boolean hasUnread
) {

}
