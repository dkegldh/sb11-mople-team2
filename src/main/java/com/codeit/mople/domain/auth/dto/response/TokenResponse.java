package com.codeit.mople.domain.auth.dto.response;

import com.codeit.mople.domain.user.dto.response.UserDto;

public record TokenResponse(
    String accessToken,
    UserDto userDto
) {}
