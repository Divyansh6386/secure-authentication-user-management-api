package com.company.starter.user.dto;

import com.company.starter.user.model.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        Role role,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
