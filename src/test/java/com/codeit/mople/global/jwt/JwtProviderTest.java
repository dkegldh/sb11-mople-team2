package com.codeit.mople.global.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.mople.domain.user.entity.Role;
import io.jsonwebtoken.JwtException;
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

  @Test
  @DisplayName("Access Token에 담은 role을 다시 꺼낼 수 있음")
  void createAccessToken_embedsRole_retrievableViaGetRole() {
    UUID userId = UUID.randomUUID();
    String token = jwtProvider.createAccessToken(userId, UUID.randomUUID().toString(), Role.ADMIN);

    assertThat(jwtProvider.getRole(token)).isEqualTo(Role.ADMIN);
  }

  @Test
  @DisplayName("role claim이 없는 토큰이면 getRole 호출 시 예외가 발생함")
  void getRole_throwsException_whenRoleClaimMissing() {
    String token = jwtProvider.createRefreshToken(UUID.randomUUID());

    assertThatThrownBy(() -> jwtProvider.getRole(token))
        .isInstanceOf(JwtException.class);
  }
}
