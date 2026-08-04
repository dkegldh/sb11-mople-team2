package com.codeit.mople.domain.playlist.entity;

import com.codeit.mople.domain.playlist.exception.PlaylistErrorCode;
import com.codeit.mople.domain.playlist.exception.PlaylistException;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "playlists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Playlist extends BaseTimeEntity {

  // userId
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String description;

  @Column(nullable = false)
  private long subscriberCount = 0L;

  private Playlist(User owner, String title, String description) {
    this.owner = owner;
    this.title = title;
    this.description = description;
  }

  public static Playlist create(User owner, String title, String description) {
    return new Playlist(owner, title, description);
  }

  public void update(String title, String description) {

    // 둘 다 Null일 경우
    if (title == null && description == null) {
      throw new PlaylistException(
          PlaylistErrorCode.PLAYLIST_UPDATE_BLANK_TITLE,
          Map.of("playlistId", getId())
      );
    }

    // 제목이 빈칸 또는 공백일 경우(설명까지 빈칸일 경우 포함)
    if (title != null && title.isBlank()) {
      throw new PlaylistException(PlaylistErrorCode.PLAYLIST_UPDATE_BLANK_TITLE,
          Map.of("playlistId", getId())
      );
    }

    // 설명이 빈칸 또는 공백일 경우
    if (description != null && description.isBlank()) {
      throw new PlaylistException(PlaylistErrorCode.PLAYLIST_UPDATE_BLANK_DESCRIPTION,
          Map.of("playlistId", getId())
      );
    }

    // 제목이 null이 아니고 비어있지 않을 때(위의 if문에서 걸러짐) 제목 갱신
    if (title != null) {
      this.title = title;
    }

    // 설명이 null이 아니고 비어있지 않을 때(위의 if문에서 걸러짐) 설명 갱신
    if (description != null) {
      this.description = description;
    }
  }
}
