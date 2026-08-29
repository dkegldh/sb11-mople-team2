package com.codeit.mople.domain.playlist.repository;

import com.codeit.mople.domain.playlist.entity.PlaylistSubscription;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistSubscriptionRepository extends JpaRepository<PlaylistSubscription, UUID> {

  boolean existsByPlaylistIdAndSubscriberId(UUID playlistId, UUID subscriberId);

  // Repository.delete()와 결과물은 동일하지만 JPA를 거쳐가냐 바로 DB로 직행하냐 차이
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      DELETE FROM PlaylistSubscription ps
             WHERE ps.playlist.id = :playlistId AND ps.subscriber.id = :subscriberId
      """)
  int deleteByPlaylistIdAndSubscriberId(
      @Param("playlistId") UUID playlistId,
      @Param("subscriberId") UUID subscriberId
  );

  @Query("""
      select ps.playlist.id
      from PlaylistSubscription ps
      where ps.subscriber.id = :subscriberId
      and ps.playlist.id in :playlistIds
      """)
  List<UUID> findPlaylistIdsBySubscriberIdAndPlaylistIdIn(
      @Param("subscriberId") UUID subscriberId,
      @Param("playlistIds") List<UUID> playlistIds);

  @Query("SELECT ps.subscriber.id FROM PlaylistSubscription ps WHERE ps.playlist.id = :playlistId")
  List<UUID> findSubscriberIdsByPlaylistId(@Param("playlistId") UUID playlistId);

  @Modifying(clearAutomatically = true)
  @Query("delete from PlaylistSubscription ps where ps.playlist.id = :playlistId")
  void deleteAllByPlaylistId(@Param("playlistId") UUID playlistId);
}
