package com.codeit.mople.domain.playlist.event;

import java.util.UUID;

public record PlaylistContentAddedEvent(
    UUID playlistContentId,
    UUID playlistId,
    UUID contentId,
    String playlistTitle
) {

}
