package com.company.starter.auth.dto;

import com.company.starter.user.model.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String email,
        Role role,
        int tokenVersion,
        OffsetDateTime disabledAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
