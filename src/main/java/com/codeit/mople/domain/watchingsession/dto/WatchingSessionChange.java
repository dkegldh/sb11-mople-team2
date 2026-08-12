package com.codeit.mople.domain.watchingsession.dto;

import java.util.UUID;

public record WatchingSessionChange(
    String contentId,
    UUID watcherId,
    String action, //"ENTER"(입장) 또는 "LEAVE"(퇴장)
    Long watcherCount //갱신된 실시간 시청자 수
) {

}
