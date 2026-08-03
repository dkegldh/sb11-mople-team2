package com.codeit.mople.domain.user.admin.dto;

import jakarta.validation.constraints.NotNull;

public record LockUpdateRequest(
    @NotNull(message = "잠금 여부는 필수입니다") Boolean locked
) {}
