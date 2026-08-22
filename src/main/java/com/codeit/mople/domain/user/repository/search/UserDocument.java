package com.codeit.mople.domain.user.repository.search;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Getter
@NoArgsConstructor
@Document(indexName = "users")
public class UserDocument {

  @Id
  private UUID id;

  @Field(type = FieldType.Wildcard)
  private String email;

  public UserDocument(UUID id, String email) {
    this.id = id;
    this.email = email;
  }

}
