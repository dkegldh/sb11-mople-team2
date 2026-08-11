package com.codeit.mople.domain.auth.security;

import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.user.entity.AuthProvider;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  private final UserRepository userRepository;

  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oAuth2User = super.loadUser(userRequest);
    return toCustomOAuth2User(oAuth2User);
  }

  CustomOAuth2User toCustomOAuth2User(OAuth2User oAuth2User) {
    String providerId = oAuth2User.getAttribute("sub");
    String email = oAuth2User.getAttribute("email");
    String name = oAuth2User.getAttribute("name");
    String picture = oAuth2User.getAttribute("picture");

    User user = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
        .orElseGet(() -> createOAuthUser(email, name, picture, providerId));

    if(user.isLocked()) {
      throw new OAuth2AuthenticationException(new OAuth2Error(
          AuthErrorCode.LOCKED_ACCOUNT.getCode(), AuthErrorCode.LOCKED_ACCOUNT.getMessage(), null));
    }

    return new CustomOAuth2User(oAuth2User, user.getId());
  }

  private User createOAuthUser(String email, String name, String picture, String providerId) {
    try {
      return userRepository.save(
          User.createOAuthUser(email, name, picture, AuthProvider.GOOGLE, providerId));
    } catch (DataIntegrityViolationException e) {
      return userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
          .orElseThrow(() -> e);
    }
  }
}
