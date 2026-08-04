package com.codeit.mople.domain.watchingsession.service;

import com.codeit.mople.domain.content.exception.ContentErrorCode;
import com.codeit.mople.domain.content.exception.ContentException;
import com.codeit.mople.domain.content.repository.ContentRepository;
import com.codeit.mople.domain.watchingsession.dto.CursorResponseWatchingSessionDto;
import com.codeit.mople.domain.watchingsession.dto.WatchingSessionContentDto;
import com.codeit.mople.domain.watchingsession.dto.WatchingSessionResponse;
import com.codeit.mople.domain.watchingsession.entity.WatchingSession;
import com.codeit.mople.domain.watchingsession.repository.WatchingSessionQueryRepository;
import com.codeit.mople.global.dto.UserSummary;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchingSessionService {

  private final WatchingSessionQueryRepository watchingSessionQueryRepository;
  private final ContentRepository contentRepository;

  @Transactional(readOnly = true)
  public CursorResponseWatchingSessionDto getWatchingSessions(
      UUID contentId, String watcherNameLike, String cursor, UUID idAfter,
      int limit, String sortDirection, String sortBy) {
    log.debug("시청 세션 목록 조회 시작 - contentId: {}", contentId);

    //콘텐츠 존재 여부 예외 처리
    if (!contentRepository.existsById(contentId)) {
      throw new ContentException(ContentErrorCode.CONTENT_NOT_FOUND, Map.of("contentId", contentId));
    }

    //limit 검증
    if (limit <= 0 || limit > 100) {
      throw new ContentException(ContentErrorCode.INVALID_PAGE_REQUEST, Map.of("limit", limit));
    }

    //정렬 기준 및 정렬 방향 정규화
    if (sortBy == null) {
      sortBy = "createdAt";
    }
    if (sortDirection == null) {
      sortDirection = "ASCENDING";
    }

    //정렬 기준 및 정렬 방향 검증
    if (!"createdAt".equalsIgnoreCase(sortBy)) {
      throw new ContentException(ContentErrorCode.INVALID_PAGE_REQUEST, Map.of("sortBy", sortBy));
    }
    if (!"ASCENDING".equalsIgnoreCase(sortDirection) &&
        !"DESCENDING".equalsIgnoreCase(sortDirection)) {
      throw new ContentException(ContentErrorCode.INVALID_PAGE_REQUEST, Map.of("sortDirection", sortDirection));
    }

    //커서 쌍 검증
    if ((cursor == null) != (idAfter == null)) {
      throw new ContentException(ContentErrorCode.INVALID_PAGE_REQUEST,
          Map.of("cursor", String.valueOf(cursor), "idAfter", String.valueOf(idAfter)));
    }

    //커서 날짜 포맷 검증(500에러를 400 Bad Request로 변환)
    if (cursor != null) {
      try {
        Instant.parse(cursor);
      } catch (DateTimeParseException e) {
        throw new ContentException(ContentErrorCode.INVALID_PAGE_REQUEST, Map.of("cursor", cursor));
      }
    }

    //QueryDSL 레포지토리 호출(limit + 1개 조회)
    List<WatchingSession> sessions = watchingSessionQueryRepository.findSessionByCursor(
        contentId, watcherNameLike, cursor, idAfter, limit, sortBy, sortDirection);

    //전체 데이터 수 카운트
    long totalCount = watchingSessionQueryRepository.countSessions(contentId, watcherNameLike);

    //hasNext 판단 및 limit 사이즈만큼 자르기
    boolean hasNext = sessions.size() > limit;
    List<WatchingSession> pageSessions = hasNext ? sessions.subList(0, limit) : sessions;

    //entity -> response dto 매핑
    List<WatchingSessionResponse> responses = pageSessions.stream()
        .map(session -> new WatchingSessionResponse(
            session.getId(),
            session.getCreatedAt(),
            new UserSummary(
                session.getUser().getId(),
                session.getUser().getName(),
                session.getUser().getProfileImageUrl()
            ),
            new WatchingSessionContentDto(
                session.getContent().getId(),
                session.getContent().getType().name(),
                session.getContent().getTitle(),
                session.getContent().getDescription(),
                session.getContent().getThumbnailUrl(),
                session.getContent().getTags(),
                session.getContent().getAverageRating(),
                session.getContent().getReviewCount()
            )
        )).toList();

    //다음 커서 값 추출
    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext && !pageSessions.isEmpty()) {
      WatchingSession lastItem = pageSessions.get(pageSessions.size() - 1);
      nextCursor = lastItem.getCreatedAt() != null ? lastItem.getCreatedAt().toString() : null;
      nextIdAfter = lastItem.getId();
    }

    //최종 CursorResponse DTO 반환
    return new CursorResponseWatchingSessionDto(
        responses,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        sortBy,
        sortDirection
    );
  }
}
