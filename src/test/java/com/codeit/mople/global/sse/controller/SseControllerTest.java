package com.codeit.mople.global.sse.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mople.domain.auth.security.CustomUserDetails;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.global.sse.service.SseService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(SseController.class)
public class SseControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private SseService sseService;

  private UUID userId;
  private CustomUserDetails userDetails;
  private SseEmitter emitter;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    userDetails = new CustomUserDetails(userId, Role.USER);
    emitter = new SseEmitter();
  }

  @Nested
  @DisplayName("SSE 연결")
  class Connect {

    @Test
    @DisplayName("SSE 연결 성공")
    void connect_success() throws Exception {
      // given
      when(sseService.connect(userId, null))
          .thenReturn(emitter);

      // when & then
      mockMvc.perform(get("/api/sse")
              .with(user(userDetails))
          )
          .andExpect(status().isOk());

      verify(sseService).connect(userId, null);
    }

    @Test
    @DisplayName("SSE 연결 성공 - lastEventId 전달")
    void connect_success_withLastEventId() throws Exception {
      // given
      UUID lastEventId = UUID.randomUUID();

      when(sseService.connect(userId, lastEventId))
          .thenReturn(emitter);

      // when & then
      mockMvc.perform(get("/api/sse")
              .with(user(userDetails))
              .param("lastEventId", lastEventId.toString())
          )
          .andExpect(status().isOk());

      verify(sseService).connect(userId, lastEventId);
    }

  }

}
