package com.codeit.mople.global.event;

import java.util.UUID;

// sessionInvalidated: 이 이벤트 발행 시점에 실제로 sessionVersion이 올라가 기존 세션이
// 무효화됐는지 여부. reason만으로 이를 추론하지 않도록(예: ACCOUNT_UNLOCKED는 세션을 끊지 않음)
// 구독자가 명시적으로 확인할 수 있게 필드로 노출한다.
public record UserForceLogoutEvent(UUID userId, ForceLogoutReason reason, boolean sessionInvalidated) {}
