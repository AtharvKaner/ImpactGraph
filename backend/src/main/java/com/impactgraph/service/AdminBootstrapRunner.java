package com.impactgraph.service;

import com.impactgraph.entity.AppUser;
import com.impactgraph.entity.AuditEventType;
import com.impactgraph.entity.Role;
import com.impactgraph.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bootstrap runner that provisions the initial admin account from environment variables.
 * <p>
 * The admin is only created if:
 * 1. ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD are set.
 * 2. No user with that email already exists.
 * 3. The password meets minimum length requirements.
 * </p>
 * <p>
 * The environment variables should be set only during initial setup and removed afterward.
 * Passwords are never logged or stored in plaintext.
 * </p>
 */
@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Value("${admin.bootstrap.email:}")
    private String bootstrapEmail;

    @Value("${admin.bootstrap.password:}")
    private String bootstrapPassword;

    public AdminBootstrapRunner(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    public void run(String... args) {
        if (bootstrapEmail == null || bootstrapEmail.isBlank()
                || bootstrapPassword == null || bootstrapPassword.isBlank()) {
            log.info("Admin bootstrap: no credentials configured, skipping.");
            return;
        }

        if (bootstrapPassword.length() < 8) {
            log.warn("Admin bootstrap: password does not meet minimum length (8 chars), skipping.");
            return;
        }

        if (userRepository.existsByEmail(bootstrapEmail)) {
            log.info("Admin bootstrap: user '{}' already exists, skipping.", bootstrapEmail);
            return;
        }

        AppUser admin = new AppUser();
        admin.setEmail(bootstrapEmail);
        admin.setDisplayName("System Admin");
        admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
        admin.setRole(Role.ADMIN);
        admin = userRepository.save(admin);

        auditService.logEvent(
                AuditEventType.USER_REGISTERED,
                admin.getId(), admin.getEmail(),
                null, admin.getId(),
                "SUCCESS",
                "Admin bootstrapped from environment variables",
                "system"
        );

        log.info("Admin bootstrap: created admin account for '{}'.", bootstrapEmail);
        log.info("IMPORTANT: Remove ADMIN_BOOTSTRAP_PASSWORD from environment after initial setup.");
    }
}
