package com.impactgraph.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactgraph.dto.ErrorResponse;
import com.impactgraph.entity.AuditEventType;
import com.impactgraph.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration.
 * <ul>
 *   <li>Stateless JWT authentication</li>
 *   <li>BCrypt password encoding</li>
 *   <li>CORS configured for the React frontend</li>
 *   <li>CSRF disabled (stateless JWT, no cookies)</li>
 *   <li>Proper 401/403 error responses (no sensitive info)</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuditService auditService;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          AuditService auditService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.auditService = auditService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable()) // Stateless JWT — no CSRF needed
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                .requestMatchers("/api/transparency/public").permitAll()
                // Admin-only endpoints
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/audit/**").hasRole("ADMIN")
                // All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                // Allow everything else (frontend static files if served)
                .anyRequest().permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    auditAccessDenied(request, "Unauthenticated access attempt");
                    writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                            "Authentication required");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    auditAccessDenied(request, "Insufficient permissions");
                    writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                            "Access denied");
                })
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String message) {
        try {
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            new ObjectMapper().writeValue(response.getWriter(),
                    new ErrorResponse(status, message));
        } catch (Exception e) {
            // Silently fail — response may already be committed
        }
    }

    private void auditAccessDenied(HttpServletRequest request, String reason) {
        try {
            String actorInfo = "anonymous";
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof JwtAuthenticatedUser user) {
                actorInfo = user.getEmail();
                auditService.logEvent(
                        AuditEventType.ACCESS_DENIED,
                        user.getUserId(),
                        user.getEmail(),
                        null, null,
                        "FAILURE",
                        "Denied: " + request.getMethod() + " " + request.getRequestURI(),
                        request.getRemoteAddr()
                );
            } else {
                auditService.logEvent(
                        AuditEventType.ACCESS_DENIED,
                        null, null, null, null,
                        "FAILURE",
                        "Unauthenticated: " + request.getMethod() + " " + request.getRequestURI(),
                        request.getRemoteAddr()
                );
            }
        } catch (Exception e) {
            // Audit failure must not break the security response
        }
    }
}
