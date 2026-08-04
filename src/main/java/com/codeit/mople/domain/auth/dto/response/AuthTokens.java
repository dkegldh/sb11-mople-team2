package com.codeit.mople.domain.auth.dto.response;

import com.codeit.mople.domain.user.dto.response.UserDto;
import java.time.Instant;

public record AuthTokens(
    String accessToken,
    String refreshToken,
    Instant refreshTokenExpiresAt,
    UserDto userDto
) {}
