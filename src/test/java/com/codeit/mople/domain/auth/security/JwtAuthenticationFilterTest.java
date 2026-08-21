package com.codeit.mople.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.auth.repository.AccountLockRepository;
import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.global.jwt.JwtProvider;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationFilterTest {

  @InjectMocks
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  @Mock
  private JwtProvider jwtProvider;

  @Mock
  private AccountLockRepository accountLockRepository;

  @Mock
  private SessionTokenRepository sessionTokenRepository;

  private final String validToken = "valid.jwt.token";
  private final UUID userId = UUID.randomUUID();
  private final String jti = "valid-jti";

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Authorization 헤더가 없으면 인증을 세팅하지 않고 다음 필터로 통과시킴")
  void doFilter_passesThrough_whenNoToken() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE)).isNull();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("유효한 토큰이면 DB 조회 없이 토큰 안의 role 클레임으로 인증 정보를 세팅함")
  void doFilter_setsAuthentication_whenTokenValid() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + validToken);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);

    given(jwtProvider.getUserId(validToken)).willReturn(userId);
    given(jwtProvider.getJti(validToken)).willReturn(jti);
    given(sessionTokenRepository.isValid(userId, jti)).willReturn(true);
    given(accountLockRepository.isLocked(userId)).willReturn(false);
    given(jwtProvider.getRole(validToken)).willReturn(Role.ADMIN);

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    UsernamePasswordAuthenticationToken auth =
        (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    CustomUserDetails principal = (CustomUserDetails) auth.getPrincipal();
    assertThat(principal.getUserId()).isEqualTo(userId);
    assertThat(principal.getRole()).isEqualTo(Role.ADMIN);
    assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE)).isNull();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("다른 기기에서 재로그인하여 세션이 무효화된 토큰이면 EXPIRED_SESSION을 세팅하고 인증하지 않음")
  void doFilter_setsExpiredSession_whenSessionInvalid() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + validToken);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);

    given(jwtProvider.getUserId(validToken)).willReturn(userId);
    given(jwtProvider.getJti(validToken)).willReturn(jti);
    given(sessionTokenRepository.isValid(userId, jti)).willReturn(false);

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE))
        .isEqualTo(AuthErrorCode.EXPIRED_SESSION);
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("관리자에 의해 잠긴 계정이면 LOCKED_ACCOUNT를 세팅하고 인증하지 않음")
  void doFilter_setsLockedAccount_whenAccountLocked() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + validToken);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);

    given(jwtProvider.getUserId(validToken)).willReturn(userId);
    given(jwtProvider.getJti(validToken)).willReturn(jti);
    given(accountLockRepository.isLocked(userId)).willReturn(true);

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE))
        .isEqualTo(AuthErrorCode.LOCKED_ACCOUNT);
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("Redis 장애로 잠금/세션 확인이 불가능하면 AUTH_SERVICE_UNAVAILABLE을 세팅하고 인증하지 않음")
  void doFilter_setsAuthServiceUnavailable_whenRedisFails() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + validToken);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);

    given(jwtProvider.getUserId(validToken)).willReturn(userId);
    given(jwtProvider.getJti(validToken)).willReturn(jti);
    given(accountLockRepository.isLocked(userId))
        .willThrow(new RedisConnectionFailureException("connection refused"));

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE))
        .isEqualTo(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("파싱할 수 없는 토큰이면 INVALID_TOKEN을 세팅하고 인증하지 않음")
  void doFilter_setsInvalidToken_whenTokenMalformed() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer broken-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);

    given(jwtProvider.getUserId("broken-token")).willThrow(ExpiredJwtException.class);

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE))
        .isEqualTo(AuthErrorCode.INVALID_TOKEN);
    verify(filterChain).doFilter(request, response);
  }
}
