package com.codeit.mople.domain.content.repository.search;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

@Getter
@NoArgsConstructor
@Document(indexName = "contents")
public class ContentDocument {

  @Id
  private UUID id;

  private String title;

  public ContentDocument(UUID id, String title) {
    this.id = id;
    this.title = title;
  }
}
