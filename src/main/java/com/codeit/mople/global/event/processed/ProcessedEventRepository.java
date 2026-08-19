package com.codeit.mople.global.event.processed;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

  // 커스텀 @Modifying 쿼리는 CrudRepository의 기본 메서드와 달리 자체 트랜잭션을 열지 않아
  // 호출부에 활성 트랜잭션이 없으면 TransactionRequiredException이 발생한다. 호출부(Kafka
  // Consumer)가 항상 트랜잭션을 갖는다고 보장할 수 없으므로 이 메서드 자체에 @Transactional을 붙인다.
  @Transactional
  @Modifying
  @Query(value = """
    INSERT INTO processed_events (event_id)
    VALUES (:eventId)
    ON CONFLICT DO NOTHING
    """, nativeQuery = true)
  int insertIfAbsent(@Param("eventId") UUID eventId);

}
