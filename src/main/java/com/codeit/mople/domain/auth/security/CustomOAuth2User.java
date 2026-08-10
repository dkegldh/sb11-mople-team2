package com.codeit.mople.domain.auth.security;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Getter
public class CustomOAuth2User implements OAuth2User {

  private final OAuth2User oAuth2User;
  private final UUID userId;

  public CustomOAuth2User(OAuth2User oAuth2User, UUID userId) {
    this.oAuth2User = oAuth2User;
    this.userId = userId;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return oAuth2User.getAttributes();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return oAuth2User.getAuthorities();
  }

  @Override
  public String getName() {
    return oAuth2User.getName();
  }
}
