package com.codeit.mople.domain.conversation.repository;

import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM Conversation c WHERE c.id = :id")
  Optional<Conversation> findWithLockById(@Param("id") UUID conversationId);

  @Query("SELECT c FROM Conversation c "
      + "JOIN FETCH c.userA JOIN FETCH c.userB "
      + "LEFT JOIN FETCH c.lastMessage lm "
      + "LEFT JOIN FETCH lm.sender "
      + "WHERE c.userA = :userA AND c.userB = :userB")
  Optional<Conversation> findWithDetailsByUserAAndUserB(@Param("userA") User userA,
      @Param("userB") User userB);
}
