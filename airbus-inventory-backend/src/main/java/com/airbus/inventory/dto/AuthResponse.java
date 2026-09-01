package com.airbus.inventory.dto;

public class AuthResponse {

    private String token;
    private String refreshToken;
    private String username;
    private String role;
    private long expiresInMs;

    public AuthResponse(String token, String refreshToken, String username, String role, long expiresInMs) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.username = username;
        this.role = role;
        this.expiresInMs = expiresInMs;
    }

    public String getToken() {
        return token;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public long getExpiresInMs() {
        return expiresInMs;
    }
}
