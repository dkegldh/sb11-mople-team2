package com.codeit.mople.domain.conversation.repository;

import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID>, ConversationRepositoryCustom {

  @Query("SELECT c FROM Conversation c JOIN FETCH c.userA JOIN FETCH c.userB "
      + "WHERE c.userA = :userA AND c.userB = :userB")
  Optional<Conversation> findByUserAAndUserB(@Param("userA") User userA,
      @Param("userB") User userB);
}
