package com.company.starter.user.repository;

import com.company.starter.user.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now "
            + "where r.userId = :userId and r.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :now")
    int deleteExpired(@Param("now") OffsetDateTime now);
}
