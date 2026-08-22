package com.codeit.mople.domain.user.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mople.domain.user.repository.search.UserDocument;
import com.codeit.mople.domain.user.repository.search.UserSearchRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 단순 단위 테스트로 시도하고 싶었으나 H2에서는 Elasticsearch를 지원하지 않음
@SpringBootTest
@ActiveProfiles("test")
class UserSearchRepositoryIntegrationTest {

  @Autowired
  private UserSearchRepository userSearchRepository;

  @BeforeEach
  void setUp() {
    userSearchRepository.deleteAll();
  }

  @Test
  @DisplayName("이메일로 사용자 검색 성공")
  void findByEmailContainingIgnoreCase_success() {
    // given
    UUID userId1 = UUID.randomUUID();
    UUID userId2 = UUID.randomUUID();
    UUID userId3 = UUID.randomUUID();

    userSearchRepository.saveAll(List.of(
        new UserDocument(userId1, "user1@test.com"),
        new UserDocument(userId2, "user2@test.com"),
        new UserDocument(userId3, "admin@test.com")
    ));

    // when
    List<UUID> result = userSearchRepository.findAllByEmailContainingIgnoreCase("test");

    // then
    assertThat(result).hasSize(3)
        .containsExactlyInAnyOrder(userId1, userId2, userId3);
  }

  @Test
  @DisplayName("검색 결과가 없으면 빈 목록 반환")
  void findByEmailContainingIgnoreCase_empty() {
    // given
    userSearchRepository.save(
        new UserDocument(
            UUID.randomUUID(),
            "user@test.com"
        )
    );

    // when
    List<UUID> result = userSearchRepository.findAllByEmailContainingIgnoreCase("what");

    // then
    assertThat(result).isEmpty();
  }
}