package com.codeit.mople.global.sse.controller;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.global.sse.controller.api.SseApi;
import com.codeit.mople.global.sse.service.SseService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class SseController implements SseApi {

  private final SseService sseService;

  @Override
  @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter connect(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam(required = false) UUID lastEventId
  ) {
    return sseService.connect(userDetails.getUserId(), lastEventId);
  }

}
