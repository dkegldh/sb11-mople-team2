package com.codeit.mople.domain.playlist.entity;

import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "playlist_subscriptions", uniqueConstraints = @UniqueConstraint(
    name = "uk_playlist_subscriptions_playlist_subscriber",
    columnNames = {"playlist_id", "subscriber_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaylistSubscription extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "playlist_id", nullable = false)
  private Playlist playlist;

  @ManyToOne(fetch =  FetchType.LAZY)
  @JoinColumn(name = "subscriber_id", nullable = false)
  private User subscriber;

  private PlaylistSubscription(Playlist playlistId, User subscriberId) {
    this.playlist = Objects.requireNonNull(playlistId);
    this.subscriber = Objects.requireNonNull(subscriberId);
  }

  public static PlaylistSubscription create(Playlist playlistId, User subscriberId) {
    return new PlaylistSubscription(playlistId, subscriberId);
  }
}
