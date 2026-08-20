package com.codeit.mople.global.jwt;

import com.codeit.mople.domain.user.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

  private static final int MIN_SECRET_BYTE_LENGTH = 32; // HMAC-SHA 최소 256bit

  private final SecretKey secretKey;
  private final long accessTokenExpiration;
  private final long refreshTokenExpiration;

  public JwtProvider(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
      @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
  ) {
    byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (secretBytes.length < MIN_SECRET_BYTE_LENGTH) {
      throw new IllegalArgumentException(
          "jwt.secret은 UTF-8 기준 최소 " + MIN_SECRET_BYTE_LENGTH + "바이트(256bit) 이상이어야 합니다. 현재 길이: "
              + secretBytes.length + "바이트");
    }

    this.secretKey = Keys.hmacShaKeyFor(secretBytes);
    this.accessTokenExpiration = accessTokenExpiration;
    this.refreshTokenExpiration = refreshTokenExpiration;
  }

  public String createAccessToken(UUID userId, String jti, Role role) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + accessTokenExpiration);

    return Jwts.builder()
        .subject(userId.toString())
        .id(jti)
        .claim("role", role.name())
        .issuedAt(now)
        .expiration(expiry)
        .signWith(secretKey)
        .compact();
  }

  public String createRefreshToken(UUID userId) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + refreshTokenExpiration);

    return Jwts.builder()
        .id(UUID.randomUUID().toString())
        .subject(userId.toString())
        .issuedAt(now)
        .expiration(expiry)
        .signWith(secretKey)
        .compact();
  }

  public Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(secretKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public UUID getUserId(String token) {
    return UUID.fromString(parseClaims(token).getSubject());
  }


  public String getJti(String token) {
    String jti = parseClaims(token).getId();
    if(jti == null) {
      throw new JwtException("jti claim이 없는 토큰입니다.");
    }
    return jti;
  }

  public Role getRole(String token) {
    String role = parseClaims(token).get("role", String.class);
    if(role == null) {
      throw new JwtException("role claim이 없는 토큰 입니다.");
    }
    return Role.valueOf(role);
  }

  public long getRefreshTokenExpiration() {
    return refreshTokenExpiration;
  }
}
