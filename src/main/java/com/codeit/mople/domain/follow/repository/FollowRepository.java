package com.codeit.mople.domain.follow.repository;

import com.codeit.mople.domain.follow.entity.Follow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

  boolean existsByFolloweeIdAndFollowerId(UUID followeeId, UUID followerId);

  Optional<Follow> findByFolloweeIdAndFollowerId(UUID followeeId, UUID followerId);

  long countByFolloweeId(UUID followeeId);

  @Query("SELECT f.follower.id FROM Follow f WHERE f.followee.id = :followeeId")
  List<UUID> findFollowerIdsByFolloweeId(@Param("followeeId") UUID followeeId);
}
