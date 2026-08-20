package com.codeit.mople.domain.playlist.repository.querydsl;

import static com.codeit.mople.domain.playlist.entity.QPlaylist.playlist;
import static com.codeit.mople.domain.playlist.entity.QPlaylistSubscription.playlistSubscription;

import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition;
import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition.PlaylistSortBy;
import com.codeit.mople.domain.playlist.entity.Playlist;
import com.codeit.mople.domain.playlist.exception.PlaylistErrorCode;
import com.codeit.mople.domain.playlist.exception.PlaylistException;
import com.codeit.mople.global.dto.SortDirection;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PlaylistRepositoryImpl implements PlaylistCustomRepository {

  private final JPAQueryFactory queryFactory;

  @Override
  public List<Playlist> findAll(PlaylistQueryCondition condition) {

    // select * from Playlist
    JPAQuery<Playlist> query = queryFactory
        .selectFrom(playlist)
        .join(playlist.owner)
        .fetchJoin();

    // inner join playlist_subscription
    // on playlist_subscription.playlist_id = playlist_id
    // 구독자 ID가 존재할때만 Inner Join(이 분기 때문에 return문에 바로 넣지 않고 selectFrom 메서드를 분리)
    if (condition.subscriberIdEqual() != null) {
      query.join(playlistSubscription).on(playlistSubscription.playlist.eq(playlist));
    }

    return query
        .where(
            keywordLike(condition.keywordLike()), // 제목(키워드 검색)
            ownerIdEqual(condition.ownerIdEqual()), // 소유자 ID
            subscriberIdEqual(condition.subscriberIdEqual()), // 구독자 ID
            cursorCondition(condition) // 커서 조건(정렬 조건, 정렬 방향)
        )
        .orderBy(sort(condition)) // order by
        .limit(condition.limit() + 1) // 다음 조회 건으로 비교
        .fetch(); // ~의 SQL문을 실행
  }

  @Override
  public long count(PlaylistQueryCondition condition) {
    JPAQuery<Long> query = queryFactory.select(playlist.count())
        .from(playlist);

    if (condition.subscriberIdEqual() != null) {
      query.join(playlistSubscription).on(playlistSubscription.playlist.eq(playlist));
    }

    Long count = query
        .where(
            keywordLike(condition.keywordLike()),
            ownerIdEqual(condition.ownerIdEqual()),
            subscriberIdEqual(condition.subscriberIdEqual())
        )
        .fetchOne(); // Long 타입으로 변환(결과가 하나만 나옴, fetch로 하면 List<Long>이 되어버림)

    return count == null ? 0L : count;
  }

  // WHERE 절
  // 제목(키워드)
  private BooleanExpression keywordLike(String keywordLike) {
    // Nullable
    if (keywordLike == null || keywordLike.isBlank()) {
      return null;
    }

    String escaped = keywordLike
        .replace(".", "..") // '.'를 이스케이프 문자로 지정
        .replace("%", ".%")
        .replace("_", "._");

    // keywordLike과 일치하는 제목을 반환
    return playlist.title.like("%" + escaped + "%", '.');
  }

  // 소유자 ID
  private BooleanExpression ownerIdEqual(UUID ownerId) {
    // Nullable
    if (ownerId == null) {
      return null;
    }

    // Playlist의 소유자 ID와 일치하는 조건식
    return playlist.owner.id.eq(ownerId);
  }

  // 구독자 ID
  private BooleanExpression subscriberIdEqual(UUID subscriberId) {
    // Nullable
    if (subscriberId == null) {
      return null;
    }

    // Playlist를 구독한 구독자 ID와 일치하는 조건식
    return playlistSubscription.subscriber.id.eq(subscriberId);
  }

  private BooleanExpression cursorCondition(PlaylistQueryCondition condition) {

    // cursor가 공백이면 예외 발생
    if (condition.cursor() != null && condition.cursor().isBlank()) {
      throw new PlaylistException(
          PlaylistErrorCode.PLAYLIST_INVALID_CURSOR,
          Map.of("cursor", condition.cursor())
      );
    }
    
    boolean hasCursor = condition.cursor() != null;
    boolean hasIdAfter = condition.idAfter() != null;

    // 커서, 보조커서 둘 중 하나만 존재하면 예외 발생(함께 존재해야 함)
    if (hasCursor != hasIdAfter) {
      throw new PlaylistException(
          PlaylistErrorCode.PLAYLIST_INVALID_CURSOR,
          Map.of(
              "cursor", condition.cursor() != null ? condition.cursor() : "null",
              "idAfter", condition.idAfter() != null ? condition.idAfter().toString() : "null"
          )
      );
    }

    // cursor, idAfter 둘 다 없으면 null 반환(첫 페이지)
    if (!hasCursor) {
      return null;
    }


    // 최신순 조건
    if (condition.sortBy() == PlaylistSortBy.UPDATED_AT) {
      // 갱신 시간은 Instant 타입(BaseTimeEntity 참조)
      Instant cursor;

      try {
        cursor = Instant.parse(condition.cursor());
      } catch (DateTimeParseException e) {
        throw new PlaylistException(
            PlaylistErrorCode.PLAYLIST_INVALID_CURSOR,
            Map.of("cursor", condition.cursor())
        );
      }

      // 경우 1 : 최신순 오름차순(=오래된 순)
      if (condition.sortDirection() == SortDirection.ASCENDING) {
        return playlist.updatedAt.gt(cursor)
            .or(
                playlist.updatedAt.eq(cursor)
                    .and(playlist.id.gt(condition.idAfter())) // tie-breaker 최종 uuid로 판단
            );
      }

      // 경우 2 : 최신순 내림차순
      return playlist.updatedAt.lt(cursor)
          .or(
              playlist.updatedAt.eq(cursor)
                  .and(playlist.id.gt(condition.idAfter()))
          );
    }

    // 구독순 조건
    if (condition.sortBy() == PlaylistSortBy.SUBSCRIBE_COUNT) {
      // 구독자수는 long 타입
      Long cursor;

      try {
        cursor = Long.parseLong(condition.cursor());
      } catch (NumberFormatException e) {
        throw new PlaylistException(
            PlaylistErrorCode.PLAYLIST_INVALID_CURSOR,
            Map.of("cursor", condition.cursor())
        );
      }

      // 구독자 수는 0 이상(음수가 들어올 경우 예외 처리)
      if (cursor < 0) {
        throw new PlaylistException(
            PlaylistErrorCode.PLAYLIST_INVALID_CURSOR,
            Map.of("cursor", condition.cursor())
        );
      }

      // 구독자 수는 0 이상(음수가 들어올 경우 예외 처리)
      if (cursor < 0) {
        throw new PlaylistException(PlaylistErrorCode.PLAYLIST_INVALID_CURSOR);
      }

      // 경우 3 : 구독순 오름차순
      if (condition.sortDirection() == SortDirection.ASCENDING) {
        return playlist.subscriberCount.gt(cursor)
            .or(
                playlist.subscriberCount.eq(cursor)
                    .and(playlist.id.gt(condition.idAfter()))
            );
      }

      // 경우 4 : 구독순 내림차순
      return playlist.subscriberCount.lt(cursor)
          .or(playlist.subscriberCount.eq(cursor)
              .and(playlist.id.gt(condition.idAfter()))
          );
    }

    return null;
  }

  // ORDER BY 절
  private OrderSpecifier<?>[] sort(PlaylistQueryCondition condition) {
    // 오름차순, 내림차순 정의
    Order order = condition.sortDirection()
        == SortDirection.ASCENDING ?
        Order.ASC : Order.DESC;

    // 최신순일 경우
    if (condition.sortBy() == PlaylistSortBy.UPDATED_AT) {
      return new OrderSpecifier[]{
          new OrderSpecifier<>(order, playlist.updatedAt),
          new OrderSpecifier<>(Order.ASC, playlist.id) // tie-breaker
      };
    }

    // 구독순일 경우
    return new OrderSpecifier[]{
        new OrderSpecifier<>(order, playlist.subscriberCount),
        new OrderSpecifier<>(Order.ASC, playlist.id) // tie-breaker
    };
  }

}
