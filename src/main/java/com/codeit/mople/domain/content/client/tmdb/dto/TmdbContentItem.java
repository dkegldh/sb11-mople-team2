package com.codeit.mople.domain.content.client.tmdb.dto;

import java.util.List;

// Movie, Tv Response를 한곳에 모음
public interface TmdbContentItem {
  Long id();
  String overview();
  String posterPath();
  List<Integer> genreIds();

  // movie, tv에서 (title, name) 각각 리턴
  String contentTitle();
}
