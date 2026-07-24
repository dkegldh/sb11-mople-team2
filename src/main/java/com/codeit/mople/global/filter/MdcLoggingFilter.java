package com.codeit.mople.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

public class MdcLoggingFilter extends HttpFilter {

  private static final String REQUEST_ID = "requestId";
  private static final String METHOD = "method";
  private static final String URL = "url";

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    try {
      MDC.put(REQUEST_ID, UUID.randomUUID().toString().substring(0, 8));
      MDC.put(METHOD, request.getMethod());
      MDC.put(URL, request.getRequestURI());

      chain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }
}
