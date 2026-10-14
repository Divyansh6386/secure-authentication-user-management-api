package com.company.starter.user.repository;

import com.company.starter.user.model.Role;
import com.company.starter.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    Optional<User> findByIdAndDisabledAtIsNull(UUID id);

    Optional<User> findByEmailAndDisabledAtIsNull(String email);

    Page<User> findAllByDisabledAtIsNull(Pageable pageable);

    long countByRoleAndDisabledAtIsNull(Role role);
}
