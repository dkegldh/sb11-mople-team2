package com.codeit.mople.domain.watchingsession.entity;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "watching_sessions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_content", columnNames = {"user_id", "content_id"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchingSession extends BaseEntity {

  //유저 - N:1 연관관계
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  //콘텐츠 - N:1 연관관계
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_id", nullable = false)
  private Content content;

  public WatchingSession(User user, Content content) {
    this.user = user;
    this.content = content;
  }
}
