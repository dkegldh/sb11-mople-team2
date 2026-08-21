package com.codeit.mople.global.event.processed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProcessedEventRepositoryTest {

  @Autowired
  private ProcessedEventRepository processedEventRepository;

  // Kafka Consumer의 handle()이 @Transactional 없이 호출하는 경우가 있어(예:
  // PlaylistContentAddedEventConsumer), 호출부 트랜잭션 유무와 무관하게 insertIfAbsent가
  // 항상 동작해야 한다. 이 메서드 자체의 @Transactional을 제거하면 TransactionRequiredException으로
  // 회귀하므로 이를 잡아내기 위한 테스트.
  @Test
  @DisplayName("호출부에 활성 트랜잭션이 없어도 insertIfAbsent는 정상 동작한다")
  void insertIfAbsent_worksWithoutSurroundingTransaction() {
    UUID eventId = UUID.randomUUID();

    assertThatCode(() -> {
      int firstInsert = processedEventRepository.insertIfAbsent(eventId);
      assertThat(firstInsert).isEqualTo(1);

      int secondInsert = processedEventRepository.insertIfAbsent(eventId);
      assertThat(secondInsert).isEqualTo(0);
    }).doesNotThrowAnyException();
  }
}
