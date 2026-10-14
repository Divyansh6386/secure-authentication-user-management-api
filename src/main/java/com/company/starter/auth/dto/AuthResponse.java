package com.company.starter.auth.dto;

import com.company.starter.user.model.Role;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        Role role
) {
}
