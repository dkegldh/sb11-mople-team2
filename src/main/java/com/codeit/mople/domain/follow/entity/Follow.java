package com.codeit.mople.domain.follow.entity;

import com.codeit.mople.global.entity.BaseEntity;
import com.codeit.mople.domain.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "follows",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_follows_followee_follower",
        columnNames = {"followee_id", "follower_id"}),
    indexes = @Index(name = "idx_follows_follower_id", columnList = "follower_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "followee_id", nullable = false)
  private User followee;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "follower_id", nullable = false)
  private User follower;

  private Follow(User followee, User follower) {
    this.followee = Objects.requireNonNull(followee, "followee");
    this.follower = Objects.requireNonNull(follower, "follower");
  }

  public static Follow create(User followee, User follower) {
    return new Follow(followee, follower);
  }
}
