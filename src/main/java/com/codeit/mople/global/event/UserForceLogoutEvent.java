package com.codeit.mople.global.event;

import java.util.UUID;

public record UserForceLogoutEvent(UUID userId, ForceLogoutReason reason) {}
