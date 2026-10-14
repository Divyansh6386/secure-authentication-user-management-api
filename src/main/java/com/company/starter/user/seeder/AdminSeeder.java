package com.company.starter.user.seeder;

import com.company.starter.config.AdminProperties;
import com.company.starter.user.model.Role;
import com.company.starter.user.model.User;
import com.company.starter.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds an initial admin account on startup. Local/dev convenience only:
 * gated behind {@code app.admin-seed.enabled=true} and the {@code local}/{@code dev}
 * profiles, so it can never run in staging or prod.
 */
@ConditionalOnProperty(
        name = "app.admin-seed.enabled",
        havingValue = "true",
        matchIfMissing = false
)
@Profile({"local", "dev"})
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeeder(
            AdminProperties adminProperties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.adminProperties = adminProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("AdminSeeder starting...");

        String email = adminProperties.email().trim().toLowerCase();
        String password = adminProperties.password().trim();

        if (email.isBlank() || password.isBlank()) {
            log.warn("AdminSeeder skipped: email or password is blank.");
            return;
        }

        if (userRepository.existsByEmail(email)) {
            log.warn("AdminSeeder skipped: email already exists.");
            return;
        }

        User admin = new User(
                email,
                passwordEncoder.encode(password),
                Role.ADMIN,
                0);

        userRepository.save(admin);
        log.info("AdminSeeder: admin user created with email={}", email);
    }
}
