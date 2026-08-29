package com.codeit.mople.global.event;

import java.time.Instant;
import java.util.UUID;

public interface PublishableEvent {

  UUID eventId();

  Instant occurredAt();
}