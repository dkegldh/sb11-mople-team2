package com.codeit.mople.domain.playlist.repository;

import com.codeit.mople.domain.playlist.entity.Playlist;
import com.codeit.mople.domain.playlist.repository.querydsl.PlaylistCustomRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistRepository extends
    JpaRepository<Playlist, UUID>,
    PlaylistCustomRepository {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE Playlist p SET p.subscriberCount = p.subscriberCount + 1 WHERE p.id = :playlistId")
  int increaseSubscriberCount(@Param("playlistId") UUID playlistId);


  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE Playlist p SET p.subscriberCount = p.subscriberCount - 1 WHERE p.id = :playlistId AND p.subscriberCount > 0")
  int decreaseSubscriberCount(@Param("playlistId") UUID playlistId);
}
