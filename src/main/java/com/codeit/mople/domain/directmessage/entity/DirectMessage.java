package com.codeit.mople.domain.directmessage.entity;

import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "direct_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectMessage extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "conversation_id", nullable = false)
  private Conversation conversation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sender_id", nullable = false)
  private User sender;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "receiver_id", nullable = false)
  private User receiver;

  @Column(nullable = false, length = 1000)
  private String content;

  public static DirectMessage createMessage(Conversation conversation, User sender, User receiver, String content) {
    DirectMessage message = new DirectMessage();
    message.conversation = conversation;
    message.sender = sender;
    message.receiver = receiver;
    message.content = content;
    return message;
  }
}
