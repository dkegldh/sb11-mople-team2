package com.codeit.mople.domain.conversation.entity;

import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "conversations",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_conversation_user_a_user_b",
            columnNames = {"user_a_id", "user_b_id"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_a_id", nullable = false)
  private User userA;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_b_id", nullable = false)
  private User userB;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "last_message_id")
  private DirectMessage lastMessage;

  private Instant userALastReadAt;

  private Instant userBLastReadAt;

  private Conversation(User userA, User userB) {
    this.userA = userA;
    this.userB = userB;
  }

  // 대화방 생성을 위한 정적 팩토리 메서드
  public static Conversation createConversation(User userA, User userB) {
    return new Conversation(userA, userB);
  }

  public void updateLastReadAt(UUID userId, Instant readAt) {
    if (this.userA.getId().equals(userId)) {
      if (this.userALastReadAt == null || readAt.isAfter(this.userALastReadAt)) {
        this.userALastReadAt = readAt;
      }
    } else if (this.userB.getId().equals(userId)) {
      if (this.userBLastReadAt == null || readAt.isAfter(this.userBLastReadAt)) {
        this.userBLastReadAt = readAt;
      }
    }
  }

  public void updateLastMessage(DirectMessage message) {
    this.lastMessage = message;
  }

  public Instant getMyLastReadAt(UUID requesterId) {
    return this.userA.getId().equals(requesterId) ? userALastReadAt : userBLastReadAt;
  }
}
