package com.impactgraph.service;

import com.impactgraph.dto.AuthResponse;
import com.impactgraph.dto.LoginRequest;
import com.impactgraph.dto.RegisterRequest;
import com.impactgraph.entity.AppUser;
import com.impactgraph.entity.AuditEventType;
import com.impactgraph.entity.Role;
import com.impactgraph.repository.UserRepository;
import com.impactgraph.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration and authentication.
 * <p>
 * Security design:
 * - Passwords are always BCrypt-hashed before storage.
 * - Login failures return a generic message; no indication whether email exists.
 * - Audit events are recorded for all login attempts.
 * - The first registered user gets ADMIN role if no admin exists yet
 *   (controlled admin bootstrap).
 * </p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    /**
     * Register a new user. The first user becomes ADMIN if no admin exists.
     * All subsequent users are DEVELOPER by default.
     *
     * @return AuthResponse with JWT token
     * @throws IllegalArgumentException if email already exists
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, String ipAddress) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        AppUser user = new AppUser();
        user.setEmail(request.getEmail());
        user.setDisplayName(request.getDisplayName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        // First user in the system becomes ADMIN
        boolean isFirstUser = userRepository.count() == 0;
        user.setRole(isFirstUser ? Role.ADMIN : Role.DEVELOPER);

        user = userRepository.save(user);

        auditService.logEvent(
                AuditEventType.USER_REGISTERED,
                user.getId(), user.getEmail(),
                null, user.getId(),
                "SUCCESS",
                "User registered" + (isFirstUser ? " as initial admin" : ""),
                ipAddress
        );

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        return new AuthResponse(
                token,
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getId()
        );
    }

    /**
     * Authenticate a user and return a JWT.
     * Returns generic error message on failure — never reveals whether the email exists.
     *
     * @return AuthResponse with JWT token
     * @throws IllegalArgumentException with generic message on failure
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, String ipAddress) {
        AppUser user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // Audit the failure — never log the attempted password
            auditService.logEvent(
                    AuditEventType.LOGIN_FAILURE,
                    null, request.getEmail(),
                    null, null,
                    "FAILURE",
                    "Invalid credentials",
                    ipAddress
            );
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (!user.isEnabled()) {
            auditService.logEvent(
                    AuditEventType.LOGIN_FAILURE,
                    user.getId(), user.getEmail(),
                    null, null,
                    "FAILURE",
                    "Account disabled",
                    ipAddress
            );
            throw new IllegalArgumentException("Invalid email or password");
        }

        auditService.logEvent(
                AuditEventType.LOGIN_SUCCESS,
                user.getId(), user.getEmail(),
                null, null,
                "SUCCESS",
                "User logged in",
                ipAddress
        );

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        return new AuthResponse(
                token,
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getId()
        );
    }
}
