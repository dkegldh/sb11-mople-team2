package com.codeit.mople.domain.conversation.repository;

import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID>,
    ConversationRepositoryCustom {

  @Query("SELECT c FROM Conversation c "
      + "JOIN FETCH c.userA JOIN FETCH c.userB "
      + "LEFT JOIN FETCH c.lastMessage lm "
      + "LEFT JOIN FETCH lm.sender "
      + "WHERE c.id = :id")
  Optional<Conversation> findWithDetailsById(@Param("id") UUID conversationId);

  @Query("SELECT c FROM Conversation c "
      + "JOIN FETCH c.userA JOIN FETCH c.userB "
      + "WHERE c.id = :id")
  Optional<Conversation> findByIdWithUsers(@Param("id") UUID conversationId);

  @Query("SELECT c FROM Conversation c "
      + "JOIN FETCH c.userA JOIN FETCH c.userB "
      + "LEFT JOIN FETCH c.lastMessage lm "
      + "LEFT JOIN FETCH lm.sender "
      + "WHERE c.userA = :userA AND c.userB = :userB")
  Optional<Conversation> findWithDetailsByUserAAndUserB(@Param("userA") User userA,
      @Param("userB") User userB);

  @Query("SELECT COUNT(c) FROM Conversation c "
      + "WHERE  c.userA.id = :participantId OR c.userB.id = :participantId")
  long countByParticipantId(@Param("participantId") UUID participantId);

  @Query("SELECT COUNT(c) > 0 FROM Conversation c "
      + "WHERE c.id = :conversationId AND (c.userA.id = :userId OR c.userB.id = :userId)")
  boolean existsByIdAndParticipantId(@Param("conversationId") UUID conversationId,
      @Param("userId") UUID userId);
}
