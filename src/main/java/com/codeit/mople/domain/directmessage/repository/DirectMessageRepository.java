package com.codeit.mople.domain.directmessage.repository;

import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID>,
    DirectMessageRepositoryCustom {

  @Query("SELECT COUNT(d) FROM DirectMessage d "
      + "WHERE d.conversation.id = :conversationId")
  long countByConversationId(@Param("conversationId") UUID conversationId);
}
