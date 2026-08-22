package com.codeit.mople.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.mople.domain.user.dto.request.UserSearchRequest;
import com.codeit.mople.domain.user.dto.request.UserSortBy;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.exception.UserException;
import com.codeit.mople.global.config.JpaAuditingConfig;
import com.codeit.mople.global.config.QueryDslConfig;
import com.codeit.mople.global.dto.SortDirection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
public class UserRepositoryTest {

  @Autowired
  private UserRepository userRepository;

  @Test
  void save_and_findByEmail_success() {
    User user = User.createUser("test@test.com", "encoded", "testUser");
    userRepository.save(user);

    var found = userRepository.findByEmail("test@test.com");

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isNotNull();
    assertThat(found.get().getCreatedAt()).isNotNull();
  }

  @Test
  void existsByEmail_returnsTrue_whenEmailExists() {
    userRepository.save(User.createUser("dup@test.com", "encoded", "duplicate"));

    boolean exists = userRepository.existsByEmail("dup@test.com");

    assertThat(exists).isTrue();
  }

  @Test
  @DisplayName("이름을 오름차순 정렬로 조회하면 이름순으로 반환됨")
  void searchUsers_sortsByNameAscending() {
    userRepository.save(User.createUser("c@test.com", "encoded", "cc"));
    userRepository.save(User.createUser("a@test.com", "encoded", "aa"));
    userRepository.save(User.createUser("b@test.com", "encoded", "bb"));

    UserSearchRequest request = new UserSearchRequest(
        null, null, null, null, null, 10,
        SortDirection.ASCENDING, UserSortBy.NAME
    );

    List<User> result = userRepository.searchUsers(request, null);

    assertThat(result).extracting(User::getName)
        .containsExactly("aa", "bb", "cc");
  }

  @Test
  @DisplayName("emailLike로 앞부분(접두어) 검색이 됨")
  void searchUsers_filtersByEmailLike() {
    User user1 = userRepository.save(
        User.createUser("test1@test.com", "encoded", "user1"));
    User user2 = userRepository.save(
        User.createUser("test2@test.com", "encoded", "user2"));
    userRepository.save(User.createUser("other@mople.com", "encoded", "user3"));

    UserSearchRequest request = new UserSearchRequest(
        "test", null, null, null, null, 10,
        SortDirection.ASCENDING, UserSortBy.NAME
    );

    List<UUID> userIds = List.of(user1.getId(), user2.getId());

    List<User> result = userRepository.searchUsers(request, userIds);

    assertThat(result).hasSize(2);
    assertThat(result).extracting(User::getEmail)
        .allMatch(email -> email.startsWith("test"));
  }

  @Test
  @DisplayName("emailLike는 대소문자를 구분하지 않고 이메일에 포함된 값을 매칭한다")
  void searchUsers_emailLike_contains_ignoresCase() {
    User user1 = userRepository.save(
        User.createUser("TestUser@Test.com", "encoded", "user1"));
    User user2 = userRepository.save(
        User.createUser("other@mople.com", "encoded", "user2"));

    UserSearchRequest upperCase = new UserSearchRequest(
        "TEST", null, null, null, null, 10,
        SortDirection.ASCENDING, UserSortBy.NAME
    );

    UserSearchRequest middleMatch = new UserSearchRequest(
        "mople", null, null, null, null, 10,
        SortDirection.ASCENDING, UserSortBy.NAME
    );

    assertThat(userRepository.searchUsers(upperCase, List.of(user1.getId()))).hasSize(1);
    assertThat(userRepository.searchUsers(middleMatch, List.of(user2.getId()))).hasSize(1);
  }

  @Test
  @DisplayName("roleEqual로 권한 필터링이 됨")
  void searchUsers_filtersByRole() {
    User admin = User.createUser("admin@test.com", "encoded", "admin");
    admin.changeRole(Role.ADMIN);
    userRepository.save(admin);
    userRepository.save(User.createUser("user@test.com", "encoded", "user"));

    UserSearchRequest request = new UserSearchRequest(
        null, Role.ADMIN, null, null, null, 10,
        SortDirection.ASCENDING, UserSortBy.NAME
    );

    List<User> result = userRepository.searchUsers(request, null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getRole()).isEqualTo(Role.ADMIN);
  }

  @Test
  @DisplayName("isLocked로 잠금 상태 필터링이 됨")
  void searchUsers_filtersByLocked() {
    User locked = User.createUser("locked@test.com", "encoded", "lockedUser");
    locked.lock();
    userRepository.save(locked);
    userRepository.save(User.createUser("normal@test.com", "encoded", "normalUser"));

    UserSearchRequest request = new UserSearchRequest(
        null, null, true, null, null, 10,
        SortDirection.ASCENDING, UserSortBy.NAME
    );

    List<User> result = userRepository.searchUsers(request, null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).isLocked()).isTrue();
  }

  @Test
  @DisplayName("커서를 넘기면 그 이후 데이터부터 조회됨")
  void searchUsers_cursorPagination_returnsNextPage() {
    User userA = userRepository.save(User.createUser("a@test.com", "encoded", "aa"));
    User userB = userRepository.save(User.createUser("b@test.com", "encoded", "bb"));
    userRepository.save(User.createUser("c@test.com", "encoded", "cc"));

    UserSearchRequest secondPageRequest = new UserSearchRequest(
        null, null, null, "bb", userB.getId(), 10,
        SortDirection.ASCENDING, UserSortBy.NAME
    );

    List<User> result = userRepository.searchUsers(secondPageRequest, null);

    assertThat(result).extracting(User::getName)
        .containsExactly("cc");
  }

  @Test
  @DisplayName("limit보다 데이터가 많으면 limit+1개가 조회됨(hasNext 판단용)")
  void searchUsers_fetchesLimitPlusOne_whenMoreDataExists() {
    for (int i = 0; i < 5; i++) {
      userRepository.save(User.createUser("user" + i + "@test.com", "encoded", "user" + i));
    }

    UserSearchRequest request = new UserSearchRequest(
        null, null, null, null, null, 3,
        SortDirection.ASCENDING, UserSortBy.NAME
    );

    List<User> result = userRepository.searchUsers(request, null);

    assertThat(result).hasSize(4);
  }

  @Test
  @DisplayName("isLocked 오름차순 - false 그룹 끝에서 true 그룹으로 정상 반환됨")
  void searchUsers_isLockedAscending_crossesGroupBoundary() {
    User normal2 = userRepository.save(User.createUser("n2@test.com", "encoded", "n2"));
    User locked1 = userRepository.save(User.createUser("l1@test.com", "encoded", "l1"));
    locked1.lock();
    userRepository.save(locked1);

    UserSearchRequest request = new UserSearchRequest(
        null, null, null, "false", normal2.getId(), 10,
        SortDirection.ASCENDING, UserSortBy.IS_LOCKED
    );

    List<User> result = userRepository.searchUsers(request, null);

    assertThat(result).extracting(User::getId)
        .containsExactly(locked1.getId());
  }

  @Test
  @DisplayName("isLocked 내림차순 - true 그룹 끝에서 false 그룹으로 정상 전환됨")
  void searchUsers_isLockedDescending_crossesGroupBoundary() {
    User locked1 = userRepository.save(User.createUser("l1@test.com", "encoded", "l1"));
    locked1.lock();
    userRepository.save(locked1);
    User normal1 = userRepository.save(User.createUser("n1@test.com", "encoded", "n1"));

    UserSearchRequest request = new UserSearchRequest(
        null, null, null, "true", locked1.getId(), 10,
        SortDirection.DESCENDING, UserSortBy.IS_LOCKED
    );

    List<User> result = userRepository.searchUsers(request, null);

    assertThat(result).extracting(User::getId)
        .containsExactly(normal1.getId());
  }

  @Test
  @DisplayName("role 오름차순 - ADMIN 그룹 끝에서 USER 그룹으로 정상 전환됨")
  void searchUsers_roleAscending_crossesGroupBoundary() {
    User admin1 = userRepository.save(User.createUser("a1@test.com", "encoded", "a1"));
    admin1.changeRole(Role.ADMIN);
    userRepository.save(admin1);
    User user1 = userRepository.save(User.createUser("u1@test.com", "encoded",  "u1"));

    UserSearchRequest request = new UserSearchRequest(
        null, null, null, "ADMIN", admin1.getId(), 10,
        SortDirection.ASCENDING, UserSortBy.ROLE
    );

    List<User> result = userRepository.searchUsers(request, null);

    assertThat(result).extracting(User::getId)
        .containsExactly(user1.getId());
  }

  @Test
  @DisplayName("role 내림차순 - USER 그룹 끝에서 ADMIN 그룹으로 정상 전환된다")
  void searchUsers_roleDescending_crossesGroupBoundary() {
    User user1 = userRepository.save(User.createUser("u1@test.com", "encoded", "일반1"));
    User admin1 = userRepository.save(User.createUser("a1@test.com", "encoded", "관리자1"));
    admin1.changeRole(Role.ADMIN);
    userRepository.save(admin1);

    UserSearchRequest request = new UserSearchRequest(
        null, null, null, "USER", user1.getId(), 10,
        SortDirection.DESCENDING, UserSortBy.ROLE
    );

    List<User> result = userRepository.searchUsers(request, null);

    assertThat(result).extracting(User::getId)
        .containsExactly(admin1.getId());
  }

  @Test
  @DisplayName("sortBy가 createdAt인데 cursor가 날짜 형식이 아니면 예외가 발생한다")
  void searchUsers_throwsException_whenCreatedAtCursorInvalid() {
    UserSearchRequest request = new UserSearchRequest(
        null, null, null, "잘못된값", UUID.randomUUID(), 10,
        SortDirection.ASCENDING, UserSortBy.CREATED_AT
    );

    assertThatThrownBy(() -> userRepository.searchUsers(request, null))
        .isInstanceOf(UserException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.INVALID_CURSOR);
  }

  @Test
  @DisplayName("sortBy가 role인데 cursor가 유효한 Role값이 아니면 예외가 발생한다")
  void searchUsers_throwsException_whenRoleCursorInvalid() {
    UserSearchRequest request = new UserSearchRequest(
        null, null, null, "SUPERADMIN", UUID.randomUUID(), 10,
        SortDirection.ASCENDING, UserSortBy.ROLE
    );

    assertThatThrownBy(() -> userRepository.searchUsers(request, null))
        .isInstanceOf(UserException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.INVALID_CURSOR);
  }

  @Test
  @DisplayName("sortBy가 isLocked인데 cursor가 true/false가 아니면 예외가 발생한다")
  void searchUsers_throwsException_whenIsLockedCursorInvalid() {
    UserSearchRequest request = new UserSearchRequest(
        null, null, null, "잠김", UUID.randomUUID(), 10,
        SortDirection.ASCENDING, UserSortBy.IS_LOCKED
    );

    assertThatThrownBy(() -> userRepository.searchUsers(request, null))
        .isInstanceOf(UserException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.INVALID_CURSOR);
  }

  @Test
  @DisplayName("cursor와 idAfter 중 하나만 전달되면 예외가 발생함")
  void searchUsers_throwsException_whenCursorAndIdAfterMismatched() {
    UserSearchRequest cursorOnly = new UserSearchRequest(
        null, null, null, "bb", null, 10,
        SortDirection.ASCENDING, UserSortBy.NAME
    );
    UserSearchRequest idAfterOnly = new UserSearchRequest(
        null, null, null, null, UUID.randomUUID(), 10,
        SortDirection.ASCENDING, UserSortBy.NAME
    );

    assertThatThrownBy(() -> userRepository.searchUsers(cursorOnly, null))
        .isInstanceOf(UserException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.INVALID_CURSOR);

    assertThatThrownBy(() -> userRepository.searchUsers(idAfterOnly, null))
        .isInstanceOf(UserException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.INVALID_CURSOR);
  }

  @Test
  @DisplayName("countUsers는 필터 조건은 반영하지만 limit에는 영향받지 않는다")
  void countUsers_reflectsFilters_ignoresLimit() {
    for (int i = 0; i < 5; i++) {
      userRepository.save(User.createUser("user" + i + "@test.com", "encoded", "user" + i));
    }
    User locked = User.createUser("locked@test.com", "encoded", "lockedUser");
    locked.lock();
    userRepository.save(locked);

    UserSearchRequest noFilter = new UserSearchRequest(
        null, null, null, null, null, 2,
        SortDirection.ASCENDING, UserSortBy.NAME
    );
    UserSearchRequest lockedOnly = new UserSearchRequest(
        null, null, true, null, null, 10,
        SortDirection.ASCENDING, UserSortBy.NAME
    );

    assertThat(userRepository.countUsers(noFilter, null)).isEqualTo(6);
    assertThat(userRepository.countUsers(lockedOnly, null)).isEqualTo(1);
  }

  @Test
  @DisplayName("countUsers는 cursor 위치와 무관하게 필터에 해당하는 전체 개수를 반환한다")
  void countUsers_ignoresCursorPosition() {
    User userA = userRepository.save(User.createUser("a@test.com", "encoded", "aa"));
    userRepository.save(User.createUser("b@test.com", "encoded", "bb"));
    userRepository.save(User.createUser("c@test.com", "encoded", "cc"));

    UserSearchRequest afterFirst = new UserSearchRequest(
        null, null, null, "aa", userA.getId(), 10,
        SortDirection.ASCENDING, UserSortBy.NAME
    );

    assertThat(userRepository.countUsers(afterFirst, null)).isEqualTo(3);
  }

  @Test
  @DisplayName("정렬값이 동일한 행이 있어도 커서 페이지네이션 시 데이터가 누락되거나 중복되지 않는다")
  void searchUsers_cursorPagination_handlesTiedSortValues() {
    userRepository.save(User.createUser("a@test.com", "encoded", "same"));
    userRepository.save(User.createUser("b@test.com", "encoded", "same"));
    userRepository.save(User.createUser("c@test.com", "encoded", "same"));

    UserSearchRequest firstPageRequest = new UserSearchRequest(
        null, null, null, null, null, 2,
        SortDirection.ASCENDING, UserSortBy.NAME
    );
    List<User> firstFetch = userRepository.searchUsers(firstPageRequest, null);
    List<User> firstPage = firstFetch.subList(0, 2);
    User lastOfFirstPage = firstPage.get(1);

    UserSearchRequest secondPageRequest = new UserSearchRequest(
        null, null, null, lastOfFirstPage.getName(), lastOfFirstPage.getId(), 2,
        SortDirection.ASCENDING, UserSortBy.NAME
    );
    List<User> secondPage = userRepository.searchUsers(secondPageRequest, null);

    List<UUID> firstPageIds = firstPage.stream().map(User::getId).toList();
    List<UUID> secondPageIds = secondPage.stream().map(User::getId).toList();

    assertThat(secondPageIds).doesNotContainAnyElementsOf(firstPageIds);
    assertThat(firstPageIds).doesNotHaveDuplicates();

    List<UUID> allIds = new ArrayList<>(firstPageIds);
    allIds.addAll(secondPageIds);
    assertThat(allIds).doesNotHaveDuplicates();
    assertThat(allIds).hasSize(3);
  }
}
