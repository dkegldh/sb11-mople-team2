package com.codeit.mople.domain.follow.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.mople.domain.follow.dto.FollowRequest;
import com.codeit.mople.domain.follow.repository.FollowRepository;
import com.codeit.mople.domain.follow.service.FollowService;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.config.KafkaProperties;
import com.codeit.mople.global.event.KafkaEventPublisher;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(FollowCreatedEventPublishTest.ProducerTestConfig.class)
@DisplayName("팔로우 생성 이벤트 발행 시점 테스트")
class FollowCreatedEventPublishTest {

  @TestConfiguration
  static class ProducerTestConfig {

    @Bean
    FollowEventProducer followEventProducer(
        KafkaEventPublisher publisher, KafkaProperties kafkaProperties) {
      return new FollowEventProducer(publisher, kafkaProperties);
    }
  }

  @MockitoBean
  KafkaEventPublisher kafkaEventPublisher;

  @Autowired
  FollowService followService;

  @Autowired
  FollowRepository followRepository;

  @Autowired
  UserRepository userRepository;

  @Autowired
  TransactionTemplate transactionTemplate;

  @Autowired
  KafkaProperties kafkaProperties;

  UUID followeeId;
  UUID followerId;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString();
    followeeId = userRepository.save(
        User.createUser("followee-" + suffix + "@mople.com", "password", "팔로우대상")).getId();
    followerId = userRepository.save(
        User.createUser("follower-" + suffix + "@mople.com", "password", "팔로워")).getId();
  }

  @AfterEach
  void tearDown() {
    followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId)
        .ifPresent(followRepository::delete);
    userRepository.deleteById(followeeId);
    userRepository.deleteById(followerId);
  }

  @Test
  @DisplayName("트랜잭션이 커밋되면 설정된 토픽으로 발행하는지")
  void publishesAfterCommit() {
    // when
    followService.follow(new FollowRequest(followeeId), followerId);

    // then
    verify(kafkaEventPublisher).publish(
        eq(kafkaProperties.topics().followCreated()),
        eq(followeeId.toString()),
        any(FollowCreatedEvent.class));
  }

  @Test
  @DisplayName("바깥 트랜잭션이 롤백되면 발행하지 않는지")
  void doesNotPublishAfterRollback() {
    // when
    assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
      followService.follow(new FollowRequest(followeeId), followerId);
      throw new IllegalStateException("강제 롤백");
    })).isInstanceOf(IllegalStateException.class);

    // then
    verifyNoInteractions(kafkaEventPublisher);
    assertThat(followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId)).isEmpty();
  }
}