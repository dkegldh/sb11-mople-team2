package com.codeit.mople.domain.playlist.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.mople.domain.playlist.exception.PlaylistErrorCode;
import com.codeit.mople.domain.playlist.exception.PlaylistUpdateBlankDescriptionException;
import com.codeit.mople.domain.playlist.exception.PlaylistUpdateBlankTitleException;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.error.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class PlaylistTest {
  
  private User owner;
  private String title;
  private String description;

  private Playlist playlist;
  
  @BeforeEach
  void setUp() {
    owner = User.createUser("test@test.com", "12345678", "test");
    title = "새 플레이리스트 (1)";
    description = "새로운 플레이리스트입니다.";

    playlist = Playlist.create(owner, title, description);
  }

  @Nested
  @DisplayName("플레이리스트 생성")
  class Create {

    @Test
    @DisplayName("플레이리스트 생성 성공")
    void create_success() {
      // given

      // BeforeEach에서 owner, title, description 초기화

      // when
      Playlist newplaylist = Playlist.create(owner, title, description);

      // then
      assertThat(newplaylist.getOwner()).isEqualTo(owner);
      assertThat(newplaylist.getTitle()).isEqualTo(title);
      assertThat(newplaylist.getDescription()).isEqualTo(description);
      assertThat(newplaylist.getSubscriberCount()).isEqualTo(0L);
    }
    
  }
  
  @Nested
  @DisplayName("플레이리스트 수정")
  class Update {
    
    @Test
    @DisplayName("플레이리스트 수정 성공 - 제목, 설명 둘 다 수정")
    void update_success() {
      // given

      // BeforeEach에서 owner, playlist 초기화

      // when
      playlist.update("수정한 제목", "수정한 설명");

      // then
      assertThat(playlist.getTitle())
          .isEqualTo("수정한 제목");

      assertThat(playlist.getDescription())
          .isEqualTo("수정한 설명");
    }

    @Test
    @DisplayName("플레이리스트 수정 성공 - 제목만 수정")
    void update_success_onlyTitle() {
      // given

      // BeforeEach에서 owner, playlist 초기화

      // when
      playlist.update("수정한 제목", null);

      // then
      assertThat(playlist.getTitle())
          .isEqualTo("수정한 제목");
    }

    @Test
    @DisplayName("플레이리스트 수정 성공 - 설명만 수정")
    void update_success_onlyDescription() {
      // given

      // BeforeEach에서 owner, playlist 초기화

      // when
      playlist.update(null, "수정한 설명");

      // then
      assertThat(playlist.getDescription())
          .isEqualTo("수정한 설명");
    }

    @Test
    @DisplayName("플레이리스트 수정 실패 - 제목에 공백만 존재")
    void update_fail_blankTitle() {
      // given

      // BeforeEach에서 owner, playlist 초기화

      // when & then
      assertThatThrownBy(() ->
          playlist.update(" ", "수정한 설명"))
          .isInstanceOf(PlaylistUpdateBlankTitleException.class)
          .extracting("errorCode")
          .isEqualTo(PlaylistErrorCode.PLAYLIST_UPDATE_BLANK_TITLE);
    }

    @Test
    @DisplayName("플레이리스트 수정 실패 - 설명에 공백만 존재")
    void update_fail_blankDescription() {
      // given

      // BeforeEach에서 owner, playlist 초기화

      // when & then
      assertThatThrownBy(() ->
          playlist.update("수정한 제목", " "))
          .isInstanceOf(PlaylistUpdateBlankDescriptionException.class)
          .extracting("errorCode")
          .isEqualTo(PlaylistErrorCode.PLAYLIST_UPDATE_BLANK_DESCRIPTION);
    }

    @Test
    @DisplayName("플레이리스트 수정 실패 - 제목과 설명이 비어있음")
    void update_fail_empty() {
      // given

      // BeforeEach에서 owner, playlist 초기화

      // when & then
      assertThatThrownBy(() ->
          playlist.update(null, null))
          .isInstanceOf(PlaylistUpdateBlankTitleException.class)
          .extracting("errorCode")
          .isEqualTo(PlaylistErrorCode.PLAYLIST_UPDATE_BLANK_TITLE);
    }

    @Test
    @DisplayName("플레이리스트 수정 실패 - 제목은 비어있고 설명이 공백")
    void update_fail_emptyTitleBlankDescription() {
      // given

      // BeforeEach에서 owner, playlist 초기화

      // when & then
      assertThatThrownBy(() ->
          playlist.update(null, ""))
          .isInstanceOf(PlaylistUpdateBlankDescriptionException.class)
          .extracting("errorCode")
          .isEqualTo(PlaylistErrorCode.PLAYLIST_UPDATE_BLANK_DESCRIPTION);
    }

    @Test
    @DisplayName("플레이리스트 수정 실패 - 제목은 공백이고 설명이 비어있음")
    void update_fail_blankTitleEmptyDescription() {
      // given

      // BeforeEach에서 owner, playlist 초기화

      // when & then
      assertThatThrownBy(() ->
          playlist.update("", null))
          .isInstanceOf(PlaylistUpdateBlankTitleException.class)
          .extracting("errorCode")
          .isEqualTo(PlaylistErrorCode.PLAYLIST_UPDATE_BLANK_TITLE);
    }

    @Test
    @DisplayName("플레이리스트 수정 실패 - 제목과 설명이 공백만 존재")
    void update_fail_blank() {
      // given

      // BeforeEach에서 owner, playlist 초기화

      // when & then
      assertThatThrownBy(() ->
          playlist.update("", ""))
          .isInstanceOf(PlaylistUpdateBlankTitleException.class)
          .extracting("errorCode")
          .isEqualTo(PlaylistErrorCode.PLAYLIST_UPDATE_BLANK_TITLE);
    }

  }

}
