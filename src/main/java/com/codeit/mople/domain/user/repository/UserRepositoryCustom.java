package com.codeit.mople.domain.user.repository;

import com.codeit.mople.domain.user.dto.request.UserSearchRequest;
import com.codeit.mople.domain.user.entity.User;
import java.util.List;

public interface UserRepositoryCustom {
  List<User> searchUsers(UserSearchRequest request);
  //검색 조건에 맞는 전체 유저 수 카운트
  long countUsers(UserSearchRequest request);
}
