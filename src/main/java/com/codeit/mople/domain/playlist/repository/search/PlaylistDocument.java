package com.codeit.mople.domain.playlist.repository.search;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

@Getter
@NoArgsConstructor
@Document(indexName = "playlists")
public class PlaylistDocument {

  @Id
  private UUID id;

  private String title;

  public PlaylistDocument(UUID id, String title) {
    this.id = id;
    this.title = title;
  }

}
