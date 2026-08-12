package com.codeit.mople.domain.auth.dto.response;

import java.time.Instant;

public record RefreshToken(
    String refreshToken,
    Instant refreshTokenExpiredAt
) {

}
