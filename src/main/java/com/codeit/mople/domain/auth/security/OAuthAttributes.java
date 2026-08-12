package com.codeit.mople.domain.auth.security;

import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.user.entity.AuthProvider;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

@Getter
@Builder
public class OAuthAttributes {

  private String providerId;
  private String email;
  private String name;
  private String picture;

  public static OAuthAttributes of(AuthProvider provider, Map<String, Object> attributes) {
    return switch (provider) {
      case GOOGLE -> ofGoogle(attributes);
      case KAKAO -> ofKakao(attributes);
      case LOCAL -> throw new IllegalArgumentException("지원하지 않는 provider 입니다: " + provider);
    };
  }

  private static OAuthAttributes ofGoogle(Map<String, Object> attributes) {
    return OAuthAttributes.builder()
        .providerId(requireProviderId(attributes.get("sub")))
        .email((String) attributes.get("email"))
        .name((String) attributes.get("name"))
        .picture((String) attributes.get("picture"))
        .build();
  }

  private static OAuthAttributes ofKakao(Map<String, Object> attributes) {
    Map<String, Object> kakaoAccount = safeMap(attributes.get("kakao_account"));
    Map<String, Object> profile = safeMap(kakaoAccount.get("profile"));
    String providerId = requireProviderId(attributes.get("id"));
    String email = (String) kakaoAccount.get("email");
    String nickname = (String) profile.get("nickname");

    return OAuthAttributes.builder()
        .providerId(providerId)
        .email(email != null ? email : "kakao_" + providerId + "@kakao.local")
        // 비즈앱 미전환 + scope에 account_email 미포함이라 이메일이 오지 않아 임시 이메일로 대체.
        // 이메일을 받을 경우 scope에 account_email 추가 + 카카오 콘솔 동의항목 설정 + 비즈앱 전환이 모두 필요함
        .name(nickname != null ? nickname : "카카오 사용자_" + providerId)
        .picture((String) profile.get("profile_image_url"))
        .build();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> safeMap(Object value) {
    return value instanceof Map<?,?> map ? (Map<String, Object>) map : Map.of();
  }

  private static String requireProviderId(Object rawId) {
    if(rawId == null) {
      throw new OAuth2AuthenticationException(new OAuth2Error(
          AuthErrorCode.OAUTH_PROVIDER_ID_MISSING.getCode(),
          AuthErrorCode.OAUTH_PROVIDER_ID_MISSING.getMessage(), null
      ));
    }
    return String.valueOf(rawId);
  }
}
