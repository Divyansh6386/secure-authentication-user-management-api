package com.company.starter.user.dto;

import com.company.starter.user.model.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull(message = "Role is required")
        Role role
) {
}
