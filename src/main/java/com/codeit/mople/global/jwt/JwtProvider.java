package com.codeit.mople.global.jwt;

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

  public String createAccessToken(UUID userId, long sessionVersion) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + accessTokenExpiration);

    return Jwts.builder()
        .subject(userId.toString())
        .claim("sessionVersion", sessionVersion)
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

  public long getSessionVersion(String token) {
    Long version = parseClaims(token).get("sessionVersion", Long.class);
    if(version == null) {
      throw new JwtException("sessionVersion claim이 없는 토큰입니다.");
    }
    return version;
  }

  public long getRefreshTokenExpiration() {
    return refreshTokenExpiration;
  }
}
