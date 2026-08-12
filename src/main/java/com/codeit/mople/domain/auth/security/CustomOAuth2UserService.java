package com.codeit.mople.domain.auth.security;

import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.user.entity.AuthProvider;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
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

  private static final String UQ_PROVIDER_ID = "uq_users_provider_provider_id";
  private static final String UQ_EMAIL = "uq_users_email";

  private final UserRepository userRepository;

  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oAuth2User = super.loadUser(userRequest);
    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    return toCustomOAuth2User(oAuth2User, registrationId);
  }

  CustomOAuth2User toCustomOAuth2User(OAuth2User oAuth2User, String registrationId) {
    AuthProvider provider = resolveProvider(registrationId);
    OAuthAttributes attributes = OAuthAttributes.of(provider, oAuth2User.getAttributes());

    User user = userRepository.findByProviderAndProviderId(provider, attributes.getProviderId())
        .orElseGet(() -> createOAuthUser(attributes, provider));

    if(user.isLocked()) {
      throw new OAuth2AuthenticationException(new OAuth2Error(
          AuthErrorCode.LOCKED_ACCOUNT.getCode(), AuthErrorCode.LOCKED_ACCOUNT.getMessage(), null));
    }

    return new CustomOAuth2User(oAuth2User, user.getId());
  }

  private User createOAuthUser(OAuthAttributes attributes, AuthProvider provider) {
    try {
      return userRepository.save(
          User.createOAuthUser(attributes.getEmail(), attributes.getName(), attributes.getPicture(), provider, attributes.getProviderId()));
    } catch (DataIntegrityViolationException e) {
      String constraintName = extractConstraintName(e);

      if(UQ_PROVIDER_ID.equals(constraintName)) {
        return userRepository.findByProviderAndProviderId(provider, attributes.getProviderId())
            .orElseThrow(() -> e);
      }
      if(UQ_EMAIL.equals(constraintName)) {
        throw new OAuth2AuthenticationException(new OAuth2Error(
            AuthErrorCode.EMAIL_ALREADY_REGISTERED.getCode(), AuthErrorCode.EMAIL_ALREADY_REGISTERED.getMessage(), null), e);
      }

      throw e;
    }
  }

  private AuthProvider resolveProvider(String registerId) {
    AuthProvider provider;
    try {
      provider = AuthProvider.valueOf(registerId.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw unsupportedProviderException(registerId);
    }

    if(provider != AuthProvider.GOOGLE && provider != AuthProvider.KAKAO) {
      throw unsupportedProviderException(registerId);
    }
    return provider;
  }

  private String extractConstraintName(DataIntegrityViolationException e) {
    if(e.getCause() instanceof ConstraintViolationException cve) {
      return cve.getConstraintName();
    }
    return null;
  }

  private OAuth2AuthenticationException unsupportedProviderException(String registrationId) {
    return new OAuth2AuthenticationException(new OAuth2Error(
        AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER.getCode(),
        AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER.getMessage() + " > " + registrationId, null));
  }
}
