package com.codeit.mople.domain.auth.security.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

  @Value("${oauth2.redirect.failure-uri}")
  private String failureUri;

  @Override
  public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException exception) throws IOException {
    String errorCode = resolveErrorCode(exception);
    String targetUri = UriComponentsBuilder.fromUriString(failureUri)
            .queryParam("error", "oauth2_authentication_failed")
            .queryParam("code", errorCode)
            .build()
            .toUriString();
    getRedirectStrategy().sendRedirect(request, response, targetUri);
  }

  private String resolveErrorCode(AuthenticationException exception) {
    if(exception instanceof OAuth2AuthenticationException oauth2Exception) {
      return oauth2Exception.getError().getErrorCode();
    }
    return "UNKNOWN";
  }
}
