package com.codeit.mople.domain.review.event;

import java.util.UUID;

public record ReviewDeletedEvent(
    UUID contentId
) {

}
