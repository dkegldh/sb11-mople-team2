package com.codeit.mople.domain.auth.service;

import com.codeit.mople.domain.auth.dto.request.SignInRequest;
import com.codeit.mople.domain.auth.dto.response.TokenResponse;
import com.codeit.mople.domain.auth.exception.AuthErrorCode;
import com.codeit.mople.domain.auth.exception.AuthException;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;

  @Transactional
  public TokenResponse signIn(SignInRequest request) {
    User user = userRepository.findByEmail(request.email())
        .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_CREDENTIALS));

    if(!passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    if(user.isLocked()) {
      throw new AuthException(AuthErrorCode.LOCKED_ACCOUNT);
    }

    long newSessionVersion = user.increaseSessionVersion();

    String accessToken = jwtProvider.createAccessToken(user.getId(), newSessionVersion);
    return new TokenResponse(accessToken);
  }
}
