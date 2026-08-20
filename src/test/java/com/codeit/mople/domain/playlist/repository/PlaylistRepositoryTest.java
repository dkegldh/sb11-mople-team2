package com.codeit.mople.domain.playlist.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition;
import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition.PlaylistSortBy;
import com.codeit.mople.domain.playlist.entity.Playlist;
import com.codeit.mople.domain.playlist.entity.PlaylistSubscription;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.config.JpaAuditingConfig;
import com.codeit.mople.global.config.QueryDslConfig;
import com.codeit.mople.global.dto.SortDirection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@DataJpaTest
public class PlaylistRepositoryTest {

  @Autowired
  private PlaylistRepository playlistRepository;

  @Autowired
  private TestEntityManager entityManager;

  private User owner;
  private User subscriber; // owner의 playlist1 구독자 및 타인 역할
  private Playlist playlist1;
  private Playlist playlist2;
  private Playlist playlist3;
  private Playlist otherPlaylist;

  @BeforeEach
  void setUp() {
    owner = User.createUser("test@test.com", "12345678", "test");
    subscriber = User.createUser("subscriber@test.com", "12345678", "subscriber");

    playlist1 =
        Playlist.create(owner, "새 플레이리스트 (1)", "새로운 플레이리스트입니다.");
    playlist2 =
        Playlist.create(owner, "새 플레이리스트 (2)", "새로운 플레이리스트입니다.");
    otherPlaylist =
        Playlist.create(subscriber, "다른 사용자의 플레이리스트", "다른 사용자의 플레이리스트입니다.");

    // 영속화
    entityManager.persist(owner);
    entityManager.persist(subscriber);
    entityManager.persist(playlist1);
    entityManager.persist(playlist2);
    entityManager.persist(otherPlaylist);

    entityManager.flush();

    // subscriber가 playlist1을 구독
    PlaylistSubscription subscription = PlaylistSubscription.create(playlist1, subscriber);
    entityManager.persist(subscription);

    entityManager.flush();

    // playlist1 구독자 수 : 2, playlist2 구독자 수 : 1(임시로 지정)
    playlistRepository.increaseSubscriberCount(playlist1.getId());
    playlistRepository.increaseSubscriberCount(playlist1.getId());
    playlistRepository.increaseSubscriberCount(playlist2.getId());

    entityManager.flush();
    entityManager.clear();
  }

  @Nested
  @DisplayName("플레이리스트 목록 조회")
  class FindAll {

    @Test
    @DisplayName("플레이리스트 목록 조회 성공 - 기본 조건")
    void findAll_success() {
      // given

      // BeforeEach에서 owner 초기화 및 playlist1, playlist2, otherPlaylist 저장

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          null,
          null,
          null,
          null,
          10,
          SortDirection.ASCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      // when
      List<Playlist> result = playlistRepository.findAll(condition);

      // then
      assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 성공 - 제목 검색 조건")
    void findAll_success_keywordLike() {
      // given

      // BeforeEach에서 playlist1 저장

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          "새 플레이리스트 (1)",
          null,
          null,
          null,
          null,
          10,
          SortDirection.ASCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      // when
      List<Playlist> result = playlistRepository.findAll(condition);

      // then
      // Playlist에 존재하는 ID만 추출하여 정확히 playlist1만 존재하는지 ID로 확인
      assertThat(result).extracting(Playlist::getId)
          .containsExactlyInAnyOrder(playlist1.getId());
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 성공 - 소유자 ID 조건")
    void findAll_success_ownerIdEqual() {
      // given

      // BeforeEach에서 owner 초기화 및 playlist1 저장

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          owner.getId(),
          null,
          null,
          null,
          10,
          SortDirection.ASCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      // when
      List<Playlist> result = playlistRepository.findAll(condition);

      // then
      assertThat(result).extracting(Playlist::getId)
          .containsExactlyInAnyOrder(playlist1.getId(), playlist2.getId());
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 성공 - 구독자 ID 조건")
    void findAll_success_subscriberIdEqual() {
      // given

      // BeforeEach에서 subscriber 초기화 및 playlist1 저장

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          null,
          subscriber.getId(),
          null,
          null,
          10,
          SortDirection.ASCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      // when
      List<Playlist> result = playlistRepository.findAll(condition);

      // then
      assertThat(result).extracting(Playlist::getId)
          .containsExactlyInAnyOrder(playlist1.getId());
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 성공 - 커서 페이지네이션 - 최신순")
    void findAll_success_cursor_updatedAt() {
      // given

      // BeforeEach에서 playlist1, playlist2, otherPlaylist 저장

      // 페이지 크기(limit)을 2로 지정(첫 번째 페이지에는 크기 결과가 3(2+1, limit+1), 두 번째 페이지에는 크기 결과가 1이 나오도록 유도)
      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          null,
          null,
          null,
          null,
          2,
          SortDirection.DESCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      // when
      List<Playlist> result = playlistRepository.findAll(condition);

      // limit + 1이기 때문에 3번째가 아닌 2번째를 선택(limit)
      Playlist last = result.get(1);

      PlaylistQueryCondition nextCondition = new PlaylistQueryCondition(
          null,
          null,
          null,
          last.getUpdatedAt().toString(),
          last.getId(),
          2,
          SortDirection.DESCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      List<Playlist> nextResult = playlistRepository.findAll(nextCondition);

      // then
      // limit + 1이기 때문에 2개가 아닌 3개가 조회되어야 함
      assertThat(result).hasSize(2 + 1);

      assertThat(nextResult).hasSize(1)
          .extracting(Playlist::getId)
          .doesNotContain(last.getId());
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 성공 - 커서 페이지네이션 - 구독순")
    void findAll_success_cursor_subscriberCount() {
      // given

      // BeforeEach에서 playlist, playlist2, otherPlaylist 저장 및 각 구독자 수 필드 크기 반영

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          null,
          null,
          null,
          null,
          2,
          SortDirection.DESCENDING,
          PlaylistSortBy.SUBSCRIBE_COUNT
      );

      // when
      List<Playlist> result = playlistRepository.findAll(condition);

      // limit + 1이기 때문에 3번째가 아닌 2번째를 선택(limit)
      Playlist last = result.get(1);

      PlaylistQueryCondition nextCondition = new PlaylistQueryCondition(
          null,
          null,
          null,
          String.valueOf(last.getSubscriberCount()),
          last.getId(),
          2,
          SortDirection.DESCENDING,
          PlaylistSortBy.SUBSCRIBE_COUNT
      );

      List<Playlist> nextResult = playlistRepository.findAll(nextCondition);

      // then
      assertThat(result).hasSize(2 + 1);
      assertThat(result.get(0).getSubscriberCount()).isEqualTo(2);
      assertThat(result.get(1).getSubscriberCount()).isEqualTo(1);

      // 구독자 수 0인 otherPlaylist만이 다음 페이지에 조회되어야 함
      assertThat(nextResult).hasSize(1)
          .extracting(Playlist::getId)
          .containsExactlyInAnyOrder(otherPlaylist.getId());
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 성공 - 커서 페이지네이션 - 구독순(오름차순)")
    void findAll_success_cursor_subscriberCount_ASC() {
      // given

      // BeforeEach에서 playlist, playlist2, otherPlaylist 저장

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          null,
          null,
          null,
          null,
          2,
          SortDirection.ASCENDING,
          PlaylistSortBy.SUBSCRIBE_COUNT
      );

      // when
      List<Playlist> result = playlistRepository.findAll(condition);

      // limit + 1이기 때문에 3번째가 아닌 2번째를 선택(limit)
      Playlist last = result.get(1);

      PlaylistQueryCondition nextCondition = new PlaylistQueryCondition(
          null,
          null,
          null,
          String.valueOf(last.getSubscriberCount()),
          last.getId(),
          2,
          SortDirection.ASCENDING,
          PlaylistSortBy.SUBSCRIBE_COUNT
      );

      List<Playlist> nextResult = playlistRepository.findAll(nextCondition);

      // then
      assertThat(result).hasSize(2 + 1);
      assertThat(result.get(0).getSubscriberCount()).isEqualTo(0);
      assertThat(result.get(1).getSubscriberCount()).isEqualTo(1);

      // playlist1만이 다음 페이지에 포함되어야 함
      assertThat(nextResult).hasSize(1)
          .extracting(Playlist::getId)
          .containsExactlyInAnyOrder(playlist1.getId());
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 성공 - 제목 검색 결과 없음")
    void findAll_success_keywordLike_notFound() {
      // given
      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          "가나다",
          null,
          null,
          null,
          null,
          10,
          SortDirection.ASCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      // when
      List<Playlist> result = playlistRepository.findAll(condition);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 성공 - 사용자가 구독한 플레이리스트가 하나도 존재하지 않음")
    void findAll_success_noSubscribedPlaylist() {
      // given
      User other = User.createUser("other@test.com", "12345678", "other");

      entityManager.persist(other);

      entityManager.flush();
      UUID otherId = other.getId();
      entityManager.clear();

      // BeforeEach에서 playlist1, playlist2, otherPlaylist 저장

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          null,
          otherId,
          null,
          null,
          10,
          SortDirection.ASCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      // when
      List<Playlist> result = playlistRepository.findAll(condition);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("플레이리스트 목록 조회 성공 - 같은 정렬값에서 idAfter 기준으로 정렬(tie-breaker 테스트)")
    void findAll_success_withCursorTieBreaker() {
      // given

      // BeforeEach에서 playlist1, playlist2 저장 및 playlist1Id, playlist3Id를 초기화
      playlist3 =
          Playlist.create(owner, "새 플레이리스트 (3)", "새로운 플레이리스트입니다.");

      entityManager.persist(playlist3);

      entityManager.flush();

      // playlist1, playlist3, otherPlaylist 구독자 수 : 2, playlist2 구독자 수 : 1(임시로 지정)
      playlistRepository.increaseSubscriberCount(playlist3.getId());
      playlistRepository.increaseSubscriberCount(playlist3.getId());
      playlistRepository.increaseSubscriberCount(otherPlaylist.getId());
      playlistRepository.increaseSubscriberCount(otherPlaylist.getId());

      entityManager.flush();
      entityManager.clear();

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          null,
          null,
          null,
          null,
          2,
          SortDirection.DESCENDING,
          PlaylistSortBy.SUBSCRIBE_COUNT
      );

      // when
      List<Playlist> result = playlistRepository.findAll(condition);

      Playlist last = result.get(1);

      PlaylistQueryCondition nextCondition = new PlaylistQueryCondition(
          null,
          null,
          null,
          String.valueOf(last.getSubscriberCount()),
          last.getId(),
          2,
          SortDirection.DESCENDING,
          PlaylistSortBy.SUBSCRIBE_COUNT
      );

      List<Playlist> nextResult = playlistRepository.findAll(nextCondition);

      // then
      assertThat(result).hasSize(2 + 1);
      assertThat(result.get(0).getSubscriberCount()).isEqualTo(2);
      assertThat(result.get(1).getSubscriberCount()).isEqualTo(2);

      // Tie-Breaker 검증(ASC이기 때문에 작은 UUID를 가진 값이 먼저 나옴
      // 이후 limit를 통해 잘림(순서는 playlist1, playlist3, otherPlaylist)
      List<UUID> sameSubscriberIds = Stream.of(playlist1, playlist3, otherPlaylist)
          .map(Playlist::getId)
          .sorted(Comparator.comparing(UUID::toString))
          .toList();

      assertThat(result)
          .extracting(Playlist::getId)
          .containsExactly(
              sameSubscriberIds.get(0),
              sameSubscriberIds.get(1),
              sameSubscriberIds.get(2)
          );

      assertThat(nextResult).hasSize(2)
          .extracting(Playlist::getId)
          .containsExactly(sameSubscriberIds.get(2), playlist2.getId());
    }

  }

  @Nested
  @DisplayName("플레이리스트 개수 조회")
  class Count {

    @Test
    @DisplayName("플레이리스트 개수 조회 성공 - 전체 조회")
    void count_success() {
      // given

      // BeforeEach에서 playlist1, playlist2, otherPlaylist 저장

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          null,
          null,
          null,
          null,
          10,
          SortDirection.ASCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      // when
      long result = playlistRepository.count(condition);

      // then
      assertThat(result).isEqualTo(3L);
    }

    @Test
    @DisplayName("플레이리스트 개수 조회 성공 - 특정 조건에 해당하는 개수 조회")
    void count_success_condition() {
      // given

      // BeforeEach에서 owner, playlist1, playlist2, otherPlaylist 저장

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          owner.getId(),
          null,
          null,
          null,
          10,
          SortDirection.ASCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      // when
      long result = playlistRepository.count(condition);

      // then
      // playlist1, playlist2가 개수에 포함
      assertThat(result).isEqualTo(2L);

    }

    @Test
    @DisplayName("플레이리스트 개수 조회 성공 - 조건에 맞는 플레이리스트가 없음")
    void count_success_noMatchingPlaylists() {
      User other = User.createUser("other@test.com", "12345678", "other");

      entityManager.persist(other);
      entityManager.flush();

      UUID otherId = other.getId();
      entityManager.clear();

      PlaylistQueryCondition condition = new PlaylistQueryCondition(
          null,
          otherId,
          null,
          null,
          null,
          10,
          SortDirection.ASCENDING,
          PlaylistSortBy.UPDATED_AT
      );

      // when
      long result = playlistRepository.count(condition);

      // then
      assertThat(result).isZero();
    }

  }
}
