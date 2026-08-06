package com.codeit.mople.domain.auth.security;

import com.codeit.mople.global.error.CommonErrorCode;
import com.codeit.mople.global.error.ErrorCode;
import com.codeit.mople.global.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException {
    ErrorCode errorCode = resolveErrorCode(request);

    response.setStatus(errorCode.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getWriter(),
        ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
  }

  private ErrorCode resolveErrorCode(HttpServletRequest request) {
    Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE);
    return attribute instanceof ErrorCode errorCode ? errorCode : CommonErrorCode.UNAUTHORIZED;
  }
}
