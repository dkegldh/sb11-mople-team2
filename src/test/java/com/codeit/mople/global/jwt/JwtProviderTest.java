package com.codeit.mople.global.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

  private final JwtProvider jwtProvider = new JwtProvider(
      "test-secret-key-for-jwt-provider-test-1234567890",
      1_800_000L,
      604_800_000L
  );

  @Test
  @DisplayName("동일한 사용자에 대해 같은 시각에 발급한 Refresh Token은 jti가 달라 서로 다른 토큰이 된다")
  void createRefreshToken_generatesDifferentTokens_forSameUserAtSameInstant() {
    UUID userId = UUID.randomUUID();

    String firstToken = jwtProvider.createRefreshToken(userId);
    String secondToken = jwtProvider.createRefreshToken(userId);

    assertThat(firstToken).isNotEqualTo(secondToken);

    String firstJti = jwtProvider.parseClaims(firstToken).getId();
    String secondJti = jwtProvider.parseClaims(secondToken).getId();
    assertThat(firstJti).isNotNull();
    assertThat(secondJti).isNotNull();
    assertThat(firstJti).isNotEqualTo(secondJti);
  }
}
