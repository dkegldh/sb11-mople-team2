package com.codeit.mople.domain.auth.security;

import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.auth.repository.AccountLockRepository;
import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.global.jwt.JwtProvider;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  public static final String AUTH_ERROR_CODE_ATTRIBUTE = "authErrorCode";

  private final JwtProvider jwtProvider;
  private final AccountLockRepository accountLockRepository;
  private final SessionTokenRepository sessionTokenRepository;

  public JwtAuthenticationFilter(JwtProvider jwtProvider, AccountLockRepository accountLockRepository,
      SessionTokenRepository sessionTokenRepository) {
    this.jwtProvider = jwtProvider;
    this.accountLockRepository = accountLockRepository;
    this.sessionTokenRepository = sessionTokenRepository;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
    throws ServletException, IOException {
    String token = resolveToken(request);

    if(token != null) {
      try {
        UUID userId = jwtProvider.getUserId(token);
        String tokenJti = jwtProvider.getJti(token);

        if(accountLockRepository.isLocked(userId)) {
          // 신원은 확인됐으나 접근이 막힌 상태 -> 인증 세팅 안 함, entry point에서 LOCKED_ACCOUNT로 401 응답
          request.setAttribute(AUTH_ERROR_CODE_ATTRIBUTE, AuthErrorCode.LOCKED_ACCOUNT);
        } else if(!sessionTokenRepository.isValid(userId, tokenJti)) {
          // 다른 기기에서 재로그인하여 세션이 만료됨 -> 인증 세팅 안 함, entry point에서 EXPIRED_SESSION으로 401 응답
          request.setAttribute(AUTH_ERROR_CODE_ATTRIBUTE, AuthErrorCode.EXPIRED_SESSION);
        } else {
          Role role = jwtProvider.getRole(token);
          CustomUserDetails principal = new CustomUserDetails(userId, role);
          var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
          SecurityContextHolder.getContext().setAuthentication(authentication);
        }
      } catch (JwtException | IllegalArgumentException e) {
        // 토큰이 만료/변조 등으로 유효하지 않으면 인증 세팅 안 함, entry point에서 INVALID_TOKEN으로 401 응답
        request.setAttribute(AUTH_ERROR_CODE_ATTRIBUTE, AuthErrorCode.INVALID_TOKEN);
      } catch (DataAccessException e) {
        log.error("Redis 장애로 인증 검증 실패 - userId 확인 불가", e);
        request.setAttribute(AUTH_ERROR_CODE_ATTRIBUTE, AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
      }
    }
    filterChain.doFilter(request, response);
  }

  private String resolveToken(HttpServletRequest request) {
    String bearer = request.getHeader("Authorization");
    if(bearer != null && bearer.startsWith("Bearer ")) {
      return bearer.substring(7);
    }
    return null;
  }
}
