package com.codeit.mople.domain.conversation.mapper;

import com.codeit.mople.domain.conversation.dto.response.ConversationDto;
import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.directmessage.dto.response.DirectMessageDto;
import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.dto.UserSummary;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ConversationMapper {

  public ConversationDto toDto(Conversation conversation, UUID requesterId) {
    User withUser = conversation.getPartnerOf(requesterId);

    UserSummary withSummary = new UserSummary(
        withUser.getId(),
        withUser.getName(),
        withUser.getProfileImageUrl()
    );

    DirectMessageDto latestMessageDto = null;
    boolean hasUnread = false;

    if (conversation.getLastMessage() != null) {
      DirectMessage lastMessage = conversation.getLastMessage();
      latestMessageDto = DirectMessageDto.from(lastMessage);

      Instant myLastReadAt = conversation.getMyLastReadAt(requesterId);

      // 마지막 메시지의 발송자가 내가 아니고 && 마지막 메시지가 내가 읽은 시간보다 뒤에 왔다면 안 읽음 처리
      if (!lastMessage.getSender().getId().equals(requesterId) &&
          ((myLastReadAt == null) || lastMessage.getCreatedAt().isAfter(myLastReadAt))) {
        hasUnread = true;
      }
    }

    return new ConversationDto(
        conversation.getId(),
        withSummary,
        latestMessageDto,
        hasUnread
    );
  }
}
