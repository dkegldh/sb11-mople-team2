package com.codeit.mople.global.event.processed;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

  @Modifying
  @Query(value = """
    INSERT INTO processed_events (event_id)
    VALUES (:eventId)
    ON CONFLICT (event_id) DO NOTHING
    """, nativeQuery = true)
  int insertIfAbsent(@Param("eventId") UUID eventId);

}
