package com.codeit.mople.global.event;

import java.util.UUID;

// sessionInvalidated: 이 이벤트 발행 시점에 실제로 sessionVersion이 올라가 기존 세션이
// 무효화됐는지 여부. reason만으로 이를 추론하지 않도록(예: ACCOUNT_UNLOCKED는 세션을 끊지 않음)
// 구독자가 명시적으로 확인할 수 있게 필드로 노출한다.
//
// 이름을 UserForceLogoutEvent가 아니라 UserAccountStatusChangedEvent로 둔 이유: 강제
// 로그아웃(세션 무효화)이 항상 일어나는 건 아니라서(ACCOUNT_UNLOCKED), "강제 로그아웃"이라는
// 이름 자체가 구독자에게 잘못된 신호를 줄 수 있다. "무엇이 일어났는가"는 reason/sessionInvalidated
// 필드로 판단하고, 이벤트 이름은 "계정 상태가 바뀌었다"는 사실만 나타낸다.
//
// 단일 JVM 전제: 이 이벤트는 ApplicationEventPublisher로 발행되며, 같은 프로세스 안에서만
// 전달된다. 현재 유일한 구독자(알림 저장)는 결과를 DB에 남기므로 인스턴스를 여러 대 띄워도
// 문제없다. 다만 향후 "그 유저의 커넥션이 붙어 있는 바로 그 인스턴스"에서 실행돼야 하는
// 구독자(예: 웹소켓 강제 종료)를 추가한다면, 로컬 이벤트만으로는 다른 인스턴스에 붙어 있는
// 커넥션에 닿지 않는다 — 그 시점에는 Redis pub/sub 등 인스턴스 간 전파 수단이 필요하다.
public record UserAccountStatusChangedEvent(UUID userId, ForceLogoutReason reason, boolean sessionInvalidated) {}
