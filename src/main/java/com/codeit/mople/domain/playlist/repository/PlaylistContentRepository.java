package com.codeit.mople.domain.playlist.repository;

import com.codeit.mople.domain.playlist.entity.PlaylistContent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistContentRepository extends JpaRepository<PlaylistContent, UUID> {

  @Query("""
      select pc
      from PlaylistContent pc
      join fetch pc.content
      where pc.playlist.id = :playlistId
      order by pc.createdAt asc
      """)
  List<PlaylistContent> findAllByPlaylistIdOrderByCreatedAtAsc(
      @Param("playlistId") UUID playlistId);

  @Query("""
    select pc
    from PlaylistContent pc
    join fetch pc.content
    join fetch pc.playlist
    where pc.playlist.id in :playlistIds
    order by pc.createdAt asc
    """)
  List<PlaylistContent> findAllByPlaylistIdInOrderByCreatedAtAsc(
      @Param("playlistIds") List<UUID> playlistIds
  );

  // 대량 데이터를 예상하여 Bulk Delete로 설정
  // playlistContentId로 삭제하면 (콘텐츠 개수+1회 조회)만큼 SQL문이 실행되기 때문에 playlistId로 삭제하도록 명시
  // clearAutomatically = true 옵션을 주어 영속성 컨텍스트(1차 캐시)에도 삭제되도록 설정(DB 삭제 후 1차 캐시가 비워짐)
  @Modifying(clearAutomatically = true)
  @Query("delete from PlaylistContent pc where pc.playlist.id = :playlistId")
  void deleteAllByPlaylistId(@Param("playlistId") UUID playlistId);

  boolean existsByPlaylistIdAndContentId(UUID playlistId, UUID contentId);

  Optional<PlaylistContent> findByPlaylistIdAndContentId(UUID playlistId, UUID contentId);
}
