package com.codeit.mople.domain.follow.repository;

import com.codeit.mople.domain.follow.entity.Follow;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

  boolean existsByFolloweeIdAndFollowerId(UUID followeeId, UUID followerId);

  Optional<Follow> findByFolloweeIdAndFollowerId(UUID followeeId, UUID followerId);

  long countByFolloweeId(UUID followeeId);
}
