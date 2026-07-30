package com.codeit.mople.domain.playlist.repository;

import com.codeit.mople.domain.playlist.entity.PlaylistSubscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistSubscriptionRepository extends JpaRepository<PlaylistSubscription, UUID> {

  boolean existsByPlaylistIdAndSubscriberId(UUID playlistId, UUID subscriberId);

  Optional<PlaylistSubscription> findByPlaylistIdAndSubscriberId(UUID playlistId, UUID subscriberId);

}
