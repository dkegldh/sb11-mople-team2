package com.codeit.mople.global.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mople.domain.conversation.entity.Conversation;
import com.codeit.mople.domain.conversation.repository.ConversationRepository;
import com.codeit.mople.domain.directmessage.repository.DirectMessageReadRedisRepository;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.scheduler.directmessage.DirectMessageReadSyncScheduler;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class DirectMessageReadSyncSchedulerTest {

  @Autowired
  private DirectMessageReadSyncScheduler scheduler;

  @Autowired
  private DirectMessageReadRedisRepository readRedisRepository;

  @Autowired
  private ConversationRepository conversationRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private RedisTemplate<String, Object> redisTemplate;

  @Autowired
  private EntityManager em;

  @AfterEach
  void tearDown() {
    // 테스트가 끝날 때마다 Redis 데이터를 비워줌
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    conversationRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("스케줄러가 실행되면 Redis의 Dirty Set 데이터를 DB에 동기화하고 대기열을 비운다.")
  void syncLastReadAtToDb_SuccessTest() {
    // given
    User userA = userRepository.save(User.createUser("testA@test.com", "12345678", "유저A"));
    User userB = userRepository.save(User.createUser("testB@test.com", "12345678", "유저B"));
    Conversation conversation = conversationRepository.save(Conversation.createConversation(userA, userB));

    UUID convId = conversation.getId();
    UUID userId = userA.getId();

    // DB의 기본 정밀도(마이크로초)와 맞추기 위해 밀리초 단위로 절삭한 현재 시각 생성
    Instant readTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    readRedisRepository.saveLastReadAt(convId, userId, readTime);

    // DB 엔티티에는 아직 읽음 시각이 반영되지 않았는지 확인
    Conversation beforeSync = conversationRepository.findById(convId).get();
    assertThat(beforeSync.getUserALastReadAt()).isNull();

    // when
    scheduler.syncLastReadAtToDb();

    // then
    Conversation afterSync = conversationRepository.findById(convId).get();
    assertThat(afterSync.getUserALastReadAt()).isEqualTo(readTime);

    Boolean hasProcessing = redisTemplate.hasKey("dm:read:dirty:processing");
    Boolean hasDirty = redisTemplate.hasKey("dm:read:dirty");
    assertThat(Boolean.TRUE.equals(hasProcessing)).isFalse();
    assertThat(Boolean.TRUE.equals(hasDirty)).isFalse();  }

  @Test
  @DisplayName("Cache-Aside 테스트: 레디스에 값이 없으면 DB에서 가져온 뒤 레디스에 복구(캐싱)한다")
  void cacheAside_MissAndHitTest() {
    // given
    User userA = userRepository.save(User.createUser("testA@test.com", "12345678", "유저A"));
    User userB = userRepository.save(User.createUser("testB@test.com", "12345678", "유저B"));
    Conversation conversation = conversationRepository.save(Conversation.createConversation(userA, userB));
    UUID convId = conversation.getId();

    Instant dbTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    // when 1: 처음 조회 (Redis는 비어있으므로 Cache Miss 발생 -> DB에서 꺼내옴)
    Optional<Instant> firstRead = readRedisRepository.getCachedLastReadAt(convId, userA.getId());

    // then 1: 비어있어야 함
    assertThat(firstRead).isEmpty();

    // when 2: 서비스 계층에서 DB 값을 꺼내와 레디스에 복구했다고 가정하고 세팅
    readRedisRepository.setCachedLastReadAt(convId, userA.getId(), dbTime);

    // when 3: 다시 조회 (이번엔 방금 캐싱되었으므로 Cache Hit 발생 -> Redis에서 바로 꺼내옴)
    Optional<Instant> secondRead = readRedisRepository.getCachedLastReadAt(convId, userA.getId());

    // then 2: 동일한 시각이 반환되어야 하며, Dirty Set에는 추가되지 않아야 함
    assertThat(secondRead).isPresent();
    assertThat(secondRead.get()).isEqualTo(dbTime);

    Boolean hasDirty = redisTemplate.hasKey("dm:read:dirty");
    assertThat(Boolean.TRUE.equals(hasDirty)).isFalse();

    // 레디스에 값이 잘 캐싱되었는지를 직접 꺼내서 검증
    String valueKey = "dm:read:" + conversation.getId() + ":" + userA.getId();
    Object redisValue = redisTemplate.opsForValue().get(valueKey);

    assertThat(redisValue).isNotNull();
    assertThat(Instant.parse(redisValue.toString())).isEqualTo(dbTime);
  }

  @Test
  @DisplayName("예외 상황: 대화방이 DB에서 삭제되었거나 레디스 값(TTL)이 만료된 경우에도 대기열에서 안전하게 지워진다.")
  void syncLastReadAtToDb_WhenConversationDeleted_ShouldCleanUp() {
    // given
    UUID fakeConvId = UUID.randomUUID();
    UUID fakeUserId = UUID.randomUUID();
    Instant readTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    // Redis에는 존재하지만 DB에는 대화방 엔티티가 없는 엣지 케이스 상황
    readRedisRepository.saveLastReadAt(fakeConvId, fakeUserId, readTime);

    // when
    scheduler.syncLastReadAtToDb();

    // then
    // DB 갱신은 실패(false)하겠지만, 무한 루프를 막기 위해 대기열에서는 깔끔하게 치워져야 함
    Boolean hasProcessing = redisTemplate.hasKey("dm:read:dirty:processing");
    Boolean hasDirty = redisTemplate.hasKey("dm:read:dirty");
    assertThat(Boolean.TRUE.equals(hasProcessing)).isFalse();
    assertThat(Boolean.TRUE.equals(hasDirty)).isFalse();
  }
}
