package com.codeit.mople.domain.playlist.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mople.domain.playlist.entity.Playlist;
import com.codeit.mople.domain.playlist.entity.PlaylistSubscription;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.config.JpaAuditingConfig;
import com.codeit.mople.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@DataJpaTest
public class PlaylistSubscriptionRepositoryTest {

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private PlaylistSubscriptionRepository playlistSubscriptionRepository;

  private UUID subscriberId;
  private UUID playlistId;
  private UUID otherPlaylistId;

  @BeforeEach
  void setUp() {
    User owner = User.createUser("owner@test.com", "12345678", "owner");
    User subscriber = User.createUser("test@test.com", "12345678", "test");

    entityManager.persist(owner);
    entityManager.persist(subscriber);
    entityManager.flush();

    Playlist playlist = Playlist.create(owner, "새 플레이리스트 (1)", "새로운 플레이리스트입니다.");
    Playlist otherPlaylist = Playlist.create(subscriber, "새 플레이리스트 (2)", "새로운 플레이리스트입니다.");

    entityManager.persist(playlist);
    entityManager.persist(otherPlaylist);

    PlaylistSubscription subscription =
        PlaylistSubscription.create(playlist, subscriber);

    entityManager.persist(subscription);

    entityManager.flush();
    entityManager.clear();

    subscriberId = subscriber.getId();
    playlistId = playlist.getId();
    otherPlaylistId = otherPlaylist.getId();

    entityManager.clear();
  }

  @Nested
  @DisplayName("사용자가 구독한 플레이리스트 ID 목록 조회")
  class FindPlaylistIdsBySubscriberIdAndPlaylistIdIn {

    @Test
    @DisplayName("사용자가 구독한 플레이리스트 ID 목록 조회 성공")
    void findPlaylistIdsBySubscriberIdAndPlaylistIdIn() {
      // given

      // BeforeEach에서 subscriberId, playlistId, otherPlaylistId 초기화

      List<UUID> playlistIds = List.of(playlistId, otherPlaylistId);

      // when
      List<UUID> result =
          playlistSubscriptionRepository.findPlaylistIdsBySubscriberIdAndPlaylistIdIn(
              subscriberId,
              playlistIds
          );

      // then
      assertThat(result).containsExactly(playlistId);
    }

    @Test
    @DisplayName("사용자가 구독한 플레이리스트 ID 목록 조회 성공 - 구독한 플레이리스트가 없으면 빈 목록 반환")
    void findPlaylistIdsBySubscriberIdAndPlaylistIdIn_empty() {
      // given

      // BeforeEach에서 playlistId, otherPlaylistId 초기화

      User another = User.createUser(
          "another@test.com",
          "12345678",
          "another"
      );

      entityManager.persist(another);
      entityManager.flush();

      // when
      List<UUID> result =
          playlistSubscriptionRepository.findPlaylistIdsBySubscriberIdAndPlaylistIdIn(
              another.getId(),
              List.of(playlistId, otherPlaylistId)
          );

      // then
      assertThat(result).isEmpty();
    }

  }

  @Nested
  @DisplayName("플레이리스트에 속해있는 구독 정보들을 삭제")
  class DeleteAllByPlaylistId {

    @Test
    @DisplayName("플레이리스트에 속해있는 구독 정보들을 삭제 성공")
    void deleteAllByPlaylistId_success() {
      // given
      // setUp()에서 playlist에 대한 구독 정보 생성 및 저장

      // when
      playlistSubscriptionRepository.deleteAllByPlaylistId(playlistId);

      // DB 삭제 후 1차 캐시가 비워지기 때문에 DB 삭제 메서드 후 호출
      entityManager.clear();

      // then
      boolean result = playlistSubscriptionRepository.existsByPlaylistIdAndSubscriberId(
          playlistId,
          subscriberId
      );

      assertThat(result).isFalse();
    }

  }

}
