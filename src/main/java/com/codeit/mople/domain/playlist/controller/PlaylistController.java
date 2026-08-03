package com.codeit.mople.domain.playlist.controller;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.playlist.controller.api.PlaylistApi;
import com.codeit.mople.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.mople.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.mople.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.mople.domain.playlist.service.PlaylistService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistController implements PlaylistApi {

  private final PlaylistService playlistService;

  @Override
  @PostMapping
  public ResponseEntity<PlaylistResponse> create(
      @AuthenticationPrincipal(errorOnInvalidType = true) CustomUserDetails userDetails,
      @Valid @RequestBody PlaylistCreateRequest request
  ) {

    PlaylistResponse response = playlistService.create(userDetails.getUserId(), request);

    return ResponseEntity
        .created(URI.create("/api/playlists/" + response.id()))
        .body(response);
  }

  @GetMapping("/{playlistId}")
  public ResponseEntity<PlaylistResponse> find(
      @PathVariable UUID playlistId
  ) {
    PlaylistResponse response = playlistService.find(playlistId);

    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{playlistId}")
  public ResponseEntity<PlaylistResponse> update(
      @PathVariable UUID playlistId,
      @RequestBody PlaylistUpdateRequest request,
      @AuthenticationPrincipal(errorOnInvalidType = true) CustomUserDetails userDetails
  ) {
    PlaylistResponse response =
        playlistService.update(playlistId, request, userDetails.getUserId());

    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{playlistId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID playlistId,
      @AuthenticationPrincipal(errorOnInvalidType = true) CustomUserDetails userDetails
  ) {
    playlistService.delete(playlistId, userDetails.getUserId());

    return ResponseEntity.noContent().build();
  }

  @Override
  @PostMapping("/{playlistId}/subscription")
  public ResponseEntity<Void> createSubscribe(
      @PathVariable UUID playlistId,
      @AuthenticationPrincipal CustomUserDetails principal
  ) {
    playlistService.subscribe(playlistId, principal.getUserId());
    return ResponseEntity.noContent().build();
  }

  @Override
  @DeleteMapping("/{playlistId}/subscription")
  public ResponseEntity<Void> cancelSubscribe(
      @PathVariable UUID playlistId,
      @AuthenticationPrincipal CustomUserDetails principal
  ) {
    playlistService.unSubscribe(playlistId, principal.getUserId());
    return ResponseEntity.noContent().build();
  }

  @Override
  @PostMapping("/{playlistId}/contents/{contentId}")
  public ResponseEntity<Void> addContentPlaylist(
      @PathVariable UUID playlistId,
      @PathVariable UUID contentId,
      @AuthenticationPrincipal CustomUserDetails principal
  ) {
    playlistService.addContent(playlistId, contentId, principal.getUserId());
    return ResponseEntity.noContent().build();
  }

  @Override
  @DeleteMapping("/{playlistId}/contents/{contentId}")
  public ResponseEntity<Void> removeContentPlaylist(
      @PathVariable UUID playlistId,
      @PathVariable UUID contentId,
      @AuthenticationPrincipal CustomUserDetails principal
  ) {
    playlistService.removeContent(playlistId, contentId, principal.getUserId());
    return ResponseEntity.noContent().build();
  }

}
