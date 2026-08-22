package com.codeit.mople.global.config;

import com.codeit.mople.domain.content.service.ContentSearchService;
import com.codeit.mople.domain.playlist.service.PlaylistSearchService;
import com.codeit.mople.domain.user.service.UserSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class SearchConfig {

  private final ContentSearchService contentSearchService;
  private final UserSearchService userSearchService;
  private final PlaylistSearchService playlistSearchService;

  @Bean
  public CommandLineRunner indexSearchDocuments() {
    return args -> {
      contentSearchService.indexAll();
      userSearchService.indexAll();
      playlistSearchService.indexAll();
    };
  }
}