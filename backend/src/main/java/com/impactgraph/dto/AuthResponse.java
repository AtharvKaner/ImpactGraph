package com.impactgraph.dto;

import java.util.UUID;

/**
 * Authentication response returned after successful login.
 * Never contains password hash or internal server details.
 */
public class AuthResponse {

    private String token;
    private String email;
    private String displayName;
    private String role;
    private UUID userId;

    public AuthResponse() {}

    public AuthResponse(String token, String email, String displayName, String role, UUID userId) {
        this.token = token;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.userId = userId;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
}
