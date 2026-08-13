package com.codeit.mople.domain.content.repository;

import com.codeit.mople.domain.content.entity.Content;
import java.util.List;
import com.codeit.mople.domain.content.entity.ContentType;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentRepository extends JpaRepository<Content, UUID> {

  // 청크 단위 중복 판정용
  @Query("SELECT c.externalId FROM Content c WHERE c.type = :type And c.externalId in :externalIds")
  List<String> findExternalIdsByTypeAndExternalIdIn(
      @Param("type") ContentType type,
      @Param("externalIds") Collection<String> externalIds);

  //배치 중복 검사용 메서드
  List<Content> findByTypeAndExternalIdIn(ContentType type, List<String> externalIds);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      update Content c set
      c.ratingSum = c.ratingSum + :rating,
      c.reviewCount = c.reviewCount + 1
      where c.id = :contentId
      """)
  void increaseRating(@Param("contentId") UUID contentId, @Param("rating") double rating);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      update Content c set
      c.ratingSum = c.ratingSum - :oldRating + :newRating
      where c.id = :contentId
      """)
  void updateRating(
      @Param("contentId") UUID contentId,
      @Param("oldRating") double oldRating,
      @Param("newRating") double newRating
  );

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      update Content c set
      c.ratingSum = c.ratingSum - :rating,
      c.reviewCount = c.reviewCount - 1
      where c.id = :contentId
      """)
  void decreaseRating(@Param("contentId") UUID contentId, @Param("rating") double rating);
}
