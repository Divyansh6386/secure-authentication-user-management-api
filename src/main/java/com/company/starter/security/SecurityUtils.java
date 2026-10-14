package com.company.starter.security;

import com.company.starter.common.error.exceptions.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Resolves the authenticated caller's id from the security context.
 *
 * <p>The JWT filter stores the user id (a UUID string) as the authentication
 * principal, so any authenticated endpoint can recover the acting user without
 * re-parsing the token. Throws {@link UnauthorizedException} when no valid
 * principal is present.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Unauthorized");
        }

        Object principal = auth.getPrincipal();
        if (principal == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        try {
            return UUID.fromString(principal.toString());
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Unauthorized");
        }
    }
}
