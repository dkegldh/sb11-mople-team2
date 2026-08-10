package com.codeit.mople.domain.playlist.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.content.entity.ContentType;
import com.codeit.mople.domain.playlist.entity.Playlist;
import com.codeit.mople.domain.playlist.entity.PlaylistContent;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.config.JpaAuditingConfig;
import com.codeit.mople.global.config.QueryDslConfig;
import java.util.List;
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
public class PlaylistContentRepositoryTest {

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private PlaylistContentRepository playlistContentRepository;
  
  private User author;
  private Playlist playlist;
  private Playlist otherPlaylist;
  private Content content1;
  private Content content2;
  private PlaylistContent playlistContent1;
  private PlaylistContent playlistContent2;
  private PlaylistContent otherPlaylistContent;

  @BeforeEach
  void setUp() {
    author = User.createUser(
        "test@test.com",
        "12345678",
        "test"
    );

    playlist = Playlist.create(
        author,
        "새로운 플레이리스트 (1)",
        "새로운 플레이리스트입니다."
    );

    otherPlaylist = Playlist.create(
        author,
        "새로운 플레이리스트 (2)",
        "새로운 플레이리스트입니다."
    );

    content1 = new Content(
        ContentType.MOVIE,
        "타이타닉",
        "설명1",
        "타이타닉.png",
        List.of("로맨스")
    );

    content2 = new Content(
        ContentType.TV_SERIES,
        "전설의 고향",
        "설명2",
        "전설의 고향.png",
        List.of("호러")
    );

    playlistContent1 =
        PlaylistContent.create(
            playlist,
            content1
        );
    playlistContent2 =
        PlaylistContent.create(
            playlist,
            content2
        );
    otherPlaylistContent =
        PlaylistContent.create(
            otherPlaylist,
            content1
        );
  }

  @Nested
  @DisplayName("플레이리스트에 콘텐츠를 추가한 순서대로 조회")
  class FindAllByPlaylistIdOrderByCreatedAtAsc {

    @Test
    @DisplayName("플레이리스트에 콘텐츠를 추가한 순서대로 조회 성공")
    void findAllByPlaylistIdOrderByCreatedAtAsc_success() throws InterruptedException {
      // given
      
      // BeforeEach에서 author, playlist, content1, content2, playlistContent1, playlistContent2 초기화

      // author, playlist, content 저장
      entityManager.persist(author);
      entityManager.persist(playlist);
      entityManager.persist(content1);
      entityManager.persist(content2);

      // playlist에 첫번째 content 등록
      entityManager.persist(playlistContent2);
      entityManager.flush();
      Thread.sleep(10);

      // playlist에 두번째 content 등록
      entityManager.persist(playlistContent1);
      entityManager.flush();

      // when
      List<PlaylistContent> result =
          playlistContentRepository.findAllByPlaylistIdOrderByCreatedAtAsc(playlist.getId());

      // then
      assertThat(result).hasSize(2);

      assertThat(result.get(0).getContent().getId())
          .isEqualTo(content2.getId());
      assertThat(result.get(1).getContent().getId())
          .isEqualTo(content1.getId());
    }

  }

  @Nested
  @DisplayName("플레이리스트에 속해있는 콘텐츠들을 삭제")
  class DeleteAllByPlaylistId {

    @Test
    @DisplayName("플레이리스트에 속해있는 콘텐츠들을 삭제 성공")
    void deleteAllByPlaylistId_success() {
      // given
      entityManager.persist(author);
      entityManager.persist(playlist);
      entityManager.persist(content1);
      entityManager.persist(content2);

      entityManager.persist(playlistContent1);
      entityManager.persist(playlistContent2);
      entityManager.flush();

      // when
      playlistContentRepository.deleteAllByPlaylistId(playlist.getId());

      // DB 삭제 후 1차 캐시가 비워지기 때문에 DB 삭제 메서드 후 호출
      entityManager.clear();

      // then
      List<PlaylistContent> result =
          playlistContentRepository.findAllByPlaylistIdOrderByCreatedAtAsc(playlist.getId());

      assertThat(result).isEmpty();
    }

  }

  @Nested
  @DisplayName("여러 플레이리스트의 콘텐츠 조회")
  class FindAllByPlaylistIdInOrderByCreatedAtAsc {

    @Test
    @DisplayName("플레이리스트 ID 목록에 해당하는 콘텐츠를 추가 순서대로 조회 성공")
    void findAllByPlaylistIdInOrderByCreatedAtAsc_success() throws InterruptedException {
      // given
      entityManager.persist(author);

      entityManager.persist(playlist);
      entityManager.persist(otherPlaylist);

      entityManager.persist(content1);
      entityManager.persist(content2);

      // playlist
      entityManager.persist(playlistContent2);
      entityManager.flush();
      Thread.sleep(10);

      entityManager.persist(playlistContent1);
      entityManager.flush();
      Thread.sleep(10);

      // otherPlaylist
      entityManager.persist(otherPlaylistContent);
      entityManager.flush();

      // when
      List<PlaylistContent> result =
          playlistContentRepository.findAllByPlaylistIdInOrderByCreatedAtAsc(
              List.of(
                  playlist.getId(),
                  otherPlaylist.getId()
              )
          );

      // then
      assertThat(result).hasSize(3);

      // createdAt 기준 정렬 확인
      assertThat(result.get(0).getContent().getId())
          .isEqualTo(content2.getId());

      assertThat(result.get(1).getContent().getId())
          .isEqualTo(content1.getId());

      assertThat(result.get(2).getPlaylist().getId())
          .isEqualTo(otherPlaylist.getId());

      // fetch join 확인
      assertThat(result.get(0).getPlaylist().getId())
          .isEqualTo(playlist.getId());

      assertThat(result.get(0).getContent())
          .isNotNull();
    }

  }

}
