package com.codeit.mople.domain.content.client.tmdb.batch;

import com.codeit.mople.domain.content.client.tmdb.dto.TmdbGenreCatalog;
import java.util.Map;

public class TmdbGenreCatalogHolder {

  private final Map<Integer, String> names;

  public TmdbGenreCatalogHolder(TmdbGenreCatalog catalog) {
    this.names = catalog.toMap();
  }

  public Map<Integer, String> names() {
    return names;
  }

  public int size() {
    return names.size();
  }
}