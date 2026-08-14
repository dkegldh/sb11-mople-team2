package com.codeit.mople.global.event.processed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

  @Id
  @Column(name = "event_id")
  private UUID eventId;

  private ProcessedEvent(UUID eventId) {
    this.eventId = eventId;
  }

  public static ProcessedEvent of(UUID eventId) {
    return new ProcessedEvent(eventId);
  }
}
