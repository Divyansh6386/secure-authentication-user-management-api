package com.company.starter.user.service;

import com.company.starter.common.error.exceptions.BadRequestException;
import com.company.starter.common.error.exceptions.ConflictException;
import com.company.starter.common.error.exceptions.NotFoundException;
import com.company.starter.common.pagination.PaginationResponse;
import com.company.starter.user.dto.UserResponse;
import com.company.starter.user.model.Role;
import com.company.starter.user.model.User;
import com.company.starter.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("createdAt", "updatedAt", "email", "role");

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public PaginationResponse<UserResponse> listUsers(int page, int size, String sort) {
        int safePage = clamp(page, 0, 100);
        int safeSize = clamp(size, 1, 50);

        SortSpec spec = parseSort(sort);

        PageRequest pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(spec.direction(), spec.field()));

        return PaginationResponse.from(
                userRepository.findAllByDisabledAtIsNull(pageable).map(this::toResponse));
    }

    public UserResponse getUserById(UUID id) {
        User user = userRepository.findByIdAndDisabledAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return toResponse(user);
    }

    @Transactional
    public UserResponse changeRole(UUID id, Role newRole, UUID actingUserId) {
        if (id.equals(actingUserId)) {
            throw new ConflictException("You cannot change your own role");
        }

        User user = userRepository.findByIdAndDisabledAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // No-op change: nothing to persist, no need to invalidate sessions.
        if (user.getRole() == newRole) {
            return toResponse(user);
        }

        // Never let the last active admin be demoted, or the system locks itself
        // out of every admin-only endpoint.
        if (user.getRole() == Role.ADMIN && newRole != Role.ADMIN) {
            ensureNotLastAdmin();
        }

        user.setRole(newRole);
        // Invalidate every existing token for this user so the privilege change
        // takes effect immediately and old sessions must re-authenticate.
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdatedAt(OffsetDateTime.now());

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void softDeleteUser(UUID id, UUID actingUserId) {
        if (id.equals(actingUserId)) {
            throw new ConflictException("You cannot delete your own account");
        }

        User user = userRepository.findByIdAndDisabledAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (user.getRole() == Role.ADMIN) {
            ensureNotLastAdmin();
        }

        OffsetDateTime now = OffsetDateTime.now();
        user.setDisabledAt(now);
        // Bump the version so any access token already in flight is rejected.
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdatedAt(now);
        userRepository.save(user);
    }

    private void ensureNotLastAdmin() {
        if (userRepository.countByRoleAndDisabledAtIsNull(Role.ADMIN) <= 1) {
            throw new ConflictException("Cannot remove the last remaining admin");
        }
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private SortSpec parseSort(String sort) {
        String[] parts = sort.split(",");
        String field = parts.length > 0 ? parts[0].trim() : "";
        if (field.isBlank()) {
            field = "createdAt";
        }

        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new BadRequestException(
                    "Invalid sort field '" + field + "'. Allowed: " + String.join(", ", ALLOWED_SORT_FIELDS));
        }

        String dirRaw = parts.length > 1 ? parts[1].trim().toLowerCase() : "";
        Sort.Direction direction = switch (dirRaw) {
            case "asc" -> Sort.Direction.ASC;
            case "desc", "" -> Sort.Direction.DESC;
            default -> throw new BadRequestException(
                    "Invalid sort direction '" + parts[1] + "'. Use 'asc' or 'desc'");
        };

        return new SortSpec(field, direction);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record SortSpec(String field, Sort.Direction direction) {
    }
}
