package com.company.starter.user.service;

import com.company.starter.common.error.exceptions.BadRequestException;
import com.company.starter.common.error.exceptions.ConflictException;
import com.company.starter.common.error.exceptions.NotFoundException;
import com.company.starter.user.model.Role;
import com.company.starter.user.model.User;
import com.company.starter.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UserServiceTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final UserService service = new UserService(repository);

    private User user(UUID id, Role role) {
        User u = new User("u@example.com", "hash", role, 0);
        u.setId(id);
        return u;
    }

    // --- sort parsing -------------------------------------------------------

    @Test
    void listUsersRejectsUnknownSortField() {
        assertThatThrownBy(() -> service.listUsers(0, 20, "ssn,desc"))
                .isInstanceOf(BadRequestException.class);

        verify(repository, never()).findAllByDisabledAtIsNull(any());
    }

    @Test
    void listUsersRejectsInvalidSortDirection() {
        assertThatThrownBy(() -> service.listUsers(0, 20, "createdAt,sideways"))
                .isInstanceOf(BadRequestException.class);
    }

    // --- changeRole guards --------------------------------------------------

    @Test
    void adminCannotChangeTheirOwnRole() {
        UUID me = UUID.randomUUID();

        assertThatThrownBy(() -> service.changeRole(me, Role.USER, me))
                .isInstanceOf(ConflictException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void demotingTheLastAdminIsBlocked() {
        UUID target = UUID.randomUUID();
        given(repository.findByIdAndDisabledAtIsNull(target))
                .willReturn(Optional.of(user(target, Role.ADMIN)));
        given(repository.countByRoleAndDisabledAtIsNull(Role.ADMIN)).willReturn(1L);

        assertThatThrownBy(() -> service.changeRole(target, Role.USER, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void demotingAnAdminBumpsTokenVersionWhenAnotherAdminRemains() {
        UUID target = UUID.randomUUID();
        User existing = user(target, Role.ADMIN);
        given(repository.findByIdAndDisabledAtIsNull(target)).willReturn(Optional.of(existing));
        given(repository.countByRoleAndDisabledAtIsNull(Role.ADMIN)).willReturn(2L);
        given(repository.save(existing)).willReturn(existing);

        var result = service.changeRole(target, Role.USER, UUID.randomUUID());

        assertThat(result.role()).isEqualTo(Role.USER);
        assertThat(existing.getTokenVersion()).isEqualTo(1);
        verify(repository).save(existing);
    }

    @Test
    void changingToTheSameRoleIsANoOp() {
        UUID target = UUID.randomUUID();
        User existing = user(target, Role.USER);
        given(repository.findByIdAndDisabledAtIsNull(target)).willReturn(Optional.of(existing));

        service.changeRole(target, Role.USER, UUID.randomUUID());

        assertThat(existing.getTokenVersion()).isEqualTo(0);
        verify(repository, never()).save(any());
        verify(repository, never()).countByRoleAndDisabledAtIsNull(any());
    }

    @Test
    void changeRoleOnAMissingUserThrowsNotFound() {
        UUID target = UUID.randomUUID();
        given(repository.findByIdAndDisabledAtIsNull(target)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole(target, Role.ADMIN, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    // --- softDelete guards --------------------------------------------------

    @Test
    void adminCannotDeleteTheirOwnAccount() {
        UUID me = UUID.randomUUID();

        assertThatThrownBy(() -> service.softDeleteUser(me, me))
                .isInstanceOf(ConflictException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void deletingTheLastAdminIsBlocked() {
        UUID target = UUID.randomUUID();
        given(repository.findByIdAndDisabledAtIsNull(target))
                .willReturn(Optional.of(user(target, Role.ADMIN)));
        given(repository.countByRoleAndDisabledAtIsNull(Role.ADMIN)).willReturn(1L);

        assertThatThrownBy(() -> service.softDeleteUser(target, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void softDeletingANormalUserDisablesAndInvalidatesSessions() {
        UUID target = UUID.randomUUID();
        User existing = user(target, Role.USER);
        given(repository.findByIdAndDisabledAtIsNull(target)).willReturn(Optional.of(existing));
        given(repository.save(existing)).willReturn(existing);

        service.softDeleteUser(target, UUID.randomUUID());

        assertThat(existing.getDisabledAt()).isNotNull();
        assertThat(existing.getTokenVersion()).isEqualTo(1);
        verify(repository).save(existing);
    }
}
