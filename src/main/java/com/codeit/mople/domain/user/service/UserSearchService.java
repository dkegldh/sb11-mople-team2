package com.codeit.mople.domain.user.service;

import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.domain.user.repository.search.UserDocument;
import com.codeit.mople.domain.user.repository.search.UserSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSearchService {

  private final UserRepository userRepository;
  private final UserSearchRepository userSearchRepository;

  @Transactional(readOnly = true)
  public void indexAll() {
    // 사용자 이메일들을 저장소에 저장함
    var documents = userRepository.findAll().stream()
        .map(user -> new UserDocument(
            user.getId(),
            user.getEmail()
        ))
        .toList();

    userSearchRepository.saveAll(documents);
  }
}