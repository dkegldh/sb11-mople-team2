package com.codeit.mople.domain.user.event;

import com.codeit.mople.global.event.PublishableEvent;
import java.util.UUID;

public record UserSearchIndexEvent(
    UUID eventId,
    UUID userId,
    String email
) implements PublishableEvent {

}