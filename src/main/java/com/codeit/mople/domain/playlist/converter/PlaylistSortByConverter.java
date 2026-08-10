package com.codeit.mople.domain.playlist.converter;

import com.codeit.mople.domain.playlist.dto.request.PlaylistQueryCondition.PlaylistSortBy;
import com.codeit.mople.domain.playlist.exception.PlaylistErrorCode;
import com.codeit.mople.domain.playlist.exception.PlaylistException;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class PlaylistSortByConverter implements Converter<String, PlaylistSortBy> {

  @Override
  public PlaylistSortBy convert(String value) {

    try {
      return PlaylistSortBy.from(value);
    } catch (IllegalArgumentException e) {
      throw new PlaylistException(
          PlaylistErrorCode.PLAYLIST_INVALID_SORT_BY,
          Map.of("sortBy", value)
      );
    }

  }

}
