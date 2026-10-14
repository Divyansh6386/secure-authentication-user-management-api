package com.company.starter.user.service;

import com.company.starter.user.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Purges expired refresh-token rows so the table does not grow without bound.
 * Rotation revokes tokens but leaves the rows for reuse detection until they
 * expire; this reclaims them afterwards.
 */
@Component
public class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanup(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = refreshTokenRepository.deleteExpired(OffsetDateTime.now());
        if (removed > 0) {
            log.info("Purged {} expired refresh token(s)", removed);
        }
    }
}
