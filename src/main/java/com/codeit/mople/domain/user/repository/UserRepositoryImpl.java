package com.codeit.mople.domain.user.repository;

import com.codeit.mople.domain.user.dto.request.UserSearchRequest;
import com.codeit.mople.domain.user.entity.QUser;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.exception.UserException;
import com.codeit.mople.global.dto.SortDirection;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryImpl implements UserRepositoryCustom{

  private final JPAQueryFactory queryFactory;

  public UserRepositoryImpl(JPAQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  private static final QUser user = QUser.user;

  @Override
  public List<User> searchUsers(UserSearchRequest request) {
    boolean isAsc = request.sortDirectionOrDefault() == SortDirection.ASCENDING;

    return queryFactory
        .selectFrom(user)
        .where(
            emailLikeCondition(request),
            roleEqualCondition(request),
            isLockedCondition(request),
            cursorCondition(request, isAsc)
        )
        .orderBy(orderSpecifiers(request, isAsc))
        .limit(request.limitOrDefault() + 1L)
        .fetch();
  }

  private BooleanExpression emailLikeCondition(UserSearchRequest request) {
    return (request.emailLike() != null && !request.emailLike().isBlank())
        ? user.email.startsWith(request.emailLike().toLowerCase(Locale.ROOT))
        : null;
  }

  private BooleanExpression roleEqualCondition(UserSearchRequest request) {
    return request.roleEqual() != null ? user.role.eq(request.roleEqual()) : null;
  }

  private BooleanExpression isLockedCondition(UserSearchRequest request) {
    return request.isLocked() != null ? user.locked.eq(request.isLocked()) : null;
  }

  private BooleanExpression cursorCondition(UserSearchRequest request, boolean isAsc) {
    if(request.cursor() == null && request.idAfter() == null) {
      return null; // 둘 다 없으면 첫 페이지 생성
    }
    if(request.cursor() == null || request.idAfter() == null) {
      throw new UserException(UserErrorCode.INVALID_CURSOR); // 한쪽만 있으면 잘못된 요청
    }

    UUID idAfter = request.idAfter();
    String cursor = request.cursor();

    return switch (request.sortByOrDefault()) {
      case NAME -> isAsc
          ? user.name.gt(cursor).or(user.name.eq(cursor).and(user.id.gt(idAfter)))
          : user.name.lt(cursor).or(user.name.eq(cursor).and(user.id.lt(idAfter)));
      case EMAIL -> isAsc
          ? user.email.gt(cursor).or(user.email.eq(cursor).and(user.id.gt(idAfter)))
          : user.email.lt(cursor).or(user.email.eq(cursor).and(user.id.lt(idAfter)));
      case CREATED_AT -> {
        Instant cursorTime = parseCursorAsInstant(cursor);
        yield isAsc
            ? user.createdAt.gt(cursorTime).or(user.createdAt.eq(cursorTime).and(user.id.gt(idAfter)))
            : user.createdAt.lt(cursorTime).or(user.createdAt.eq(cursorTime).and(user.id.lt(idAfter)));
      }
      case IS_LOCKED -> {
        boolean cursorLocked = parseCursorAsBoolean(cursor);
        yield isAsc
            ? sameGroupOrAfter(user.locked.eq(false), user.locked.eq(true), !cursorLocked, idAfter, true)
            : sameGroupOrAfter(user.locked.eq(true), user.locked.eq(false), cursorLocked, idAfter, false);
      }
      case ROLE -> {
        Role cursorRole = parseCursorAsRole(cursor);
        String cursorRoleValue = cursorRole.name();
        yield isAsc
            ? user.role.stringValue().gt(cursorRoleValue)
                .or(user.role.stringValue().eq(cursorRoleValue).and(user.id.gt(idAfter)))
            : user.role.stringValue().lt(cursorRoleValue)
                .or(user.role.stringValue().eq(cursorRoleValue).and(user.id.lt(idAfter)));
      }
    };
  }

  private OrderSpecifier<?>[] orderSpecifiers(UserSearchRequest request, boolean isAsc) {
    OrderSpecifier<?> primary = switch (request.sortByOrDefault()) {
      case NAME -> isAsc ? user.name.asc() : user.name.desc();
      case EMAIL -> isAsc ? user.email.asc() : user.email.desc();
      case CREATED_AT -> isAsc ? user.createdAt.asc() : user.createdAt.desc();
      case IS_LOCKED -> isAsc ? user.locked.asc() : user.locked.desc();
      case ROLE -> isAsc ? user.role.asc() : user.role.desc();
    };
    OrderSpecifier<?> secondary = isAsc ? user.id.asc() : user.id.desc();
    return new OrderSpecifier[]{primary, secondary};
  }

  private BooleanExpression sameGroupOrAfter(
      BooleanExpression firstGroup, BooleanExpression secondGroup,
      boolean isInFirstGroup, UUID idAfter, boolean useGreaterThan
  ) {
    BooleanExpression idCompare = useGreaterThan ? user.id.gt(idAfter) : user.id.lt(idAfter);

    return isInFirstGroup
        ? firstGroup.and(idCompare).or(secondGroup)
        :secondGroup.and(idCompare);
  }

  private Instant parseCursorAsInstant(String cursor) {
    try {
      return Instant.parse(cursor);
    } catch (DateTimeParseException e) {
      throw new UserException(UserErrorCode.INVALID_CURSOR);
    }
  }

  private boolean parseCursorAsBoolean(String cursor) {
    if(!"true".equalsIgnoreCase(cursor) && !"false".equalsIgnoreCase(cursor)) {
      throw new UserException(UserErrorCode.INVALID_CURSOR);
    }
    return Boolean.parseBoolean(cursor);
  }

  private Role parseCursorAsRole(String cursor) {
    try {
      return Role.valueOf(cursor);
    } catch (IllegalArgumentException e) {
      throw new UserException(UserErrorCode.INVALID_CURSOR);
    }
  }

  @Override
  public long countUsers(UserSearchRequest request) {
    Long total = queryFactory
        .select(user.count())
        .from(user)
        .where(
            emailLikeCondition(request),
            roleEqualCondition(request),
            isLockedCondition(request)
            //totalCount는 전체 개수이므로 커서 조건(cursorCondition)은 제외
        )
        .fetchOne();

    return total != null ? total : 0L;
  }
}
