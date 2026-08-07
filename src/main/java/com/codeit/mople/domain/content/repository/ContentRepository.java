package com.codeit.mople.domain.content.repository;

import com.codeit.mople.domain.content.entity.Content;
import java.util.List;
import com.codeit.mople.domain.content.entity.ContentType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentRepository extends JpaRepository<Content, UUID> {

  //배치 중복 검사용 메서드
  List<Content> findByExternalIdIn(List<String> externalIds);
  boolean existsByTypeAndTitle(ContentType type, String title);

}
