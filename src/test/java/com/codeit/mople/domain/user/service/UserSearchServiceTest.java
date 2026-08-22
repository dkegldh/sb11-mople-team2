package com.codeit.mople.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.domain.user.repository.search.UserDocument;
import com.codeit.mople.domain.user.repository.search.UserSearchRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSearchServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserSearchRepository userSearchRepository;

  @InjectMocks
  private UserSearchService userSearchService;

  private UUID userId1;
  private UUID userId2;
  private User user1;
  private User user2;

  @BeforeEach
  void setUp() {
    userId1 = UUID.randomUUID();
    userId2 = UUID.randomUUID();

    user1 = mock(User.class);
    user2 = mock(User.class);
  }

  @Test
  @DisplayName("검색 문서로 저장 성공")
  void indexAll_success() {
    // given

    // BeforeEach에서 user1, user2를 초기화

    when(user1.getId()).thenReturn(userId1);
    when(user1.getEmail()).thenReturn("user1@test.com");

    when(user2.getId()).thenReturn(userId2);
    when(user2.getEmail()).thenReturn("user2@test.com");

    when(userRepository.findAll())
        .thenReturn(List.of(user1, user2));

    // when
    userSearchService.indexAll();

    // then
    ArgumentCaptor<List<UserDocument>> captor = ArgumentCaptor.forClass(List.class);

    verify(userSearchRepository).saveAll(captor.capture());

    List<UserDocument> documents = captor.getValue();

    assertThat(documents).hasSize(2);

    assertThat(documents)
        .extracting(UserDocument::getId)
        .containsExactly(userId1, userId2);

    assertThat(documents)
        .extracting(UserDocument::getEmail)
        .containsExactly("user1@test.com", "user2@test.com");
  }

  @Test
  @DisplayName("검색 문서로 저장 성공 - 사용자가 없을 경우 빈 검색 문서를 저장")
  void indexAll_success_empty() {
    // given
    when(userRepository.findAll())
        .thenReturn(List.of());

    // when
    userSearchService.indexAll();

    // then
    ArgumentCaptor<List<UserDocument>> captor = ArgumentCaptor.forClass(List.class);

    verify(userSearchRepository).saveAll(captor.capture());

    assertThat(captor.getValue()).isEmpty();
  }
}