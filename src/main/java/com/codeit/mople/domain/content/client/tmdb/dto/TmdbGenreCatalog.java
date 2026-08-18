package com.codeit.mople.domain.content.client.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbGenreCatalog(List<Entry> entries) {

  private static final TmdbGenreCatalog EMPTY = new TmdbGenreCatalog(List.of());

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Entry(Integer id, String name) {

  }
  
  public static TmdbGenreCatalog empty() {
    return EMPTY;
  }

  // 장르를 hashMap 객체로 만들어 놓은것을 list형태로 변환
  public static TmdbGenreCatalog from(Map<Integer, String> names) {
    return new TmdbGenreCatalog(names.entrySet().stream()
        .map(entry -> new Entry(entry.getKey(), entry.getValue()))
        .toList());
  }
  
  @JsonIgnore
  public boolean isEmpty() {
    return entries.isEmpty();
  }

  public int size() {
    return entries.size();
  }

  public Map<Integer, String> toMap() {
    Map<Integer, String> names = new LinkedHashMap<>();
    for (Entry entry : entries) {
      names.put(entry.id(), entry.name());
    }
    return Map.copyOf(names);
  }
}