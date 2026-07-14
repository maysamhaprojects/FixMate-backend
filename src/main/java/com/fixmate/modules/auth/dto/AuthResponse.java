package com.fixmate.modules.auth.dto;

public class AuthResponse {

    private String token;
    private String role;
    private String fullName;

    public AuthResponse(String token, String role, String fullName) {
        this.token = token;
        this.role = role;
        this.fullName = fullName;
    }

    public String getToken() { return token; }
    public String getRole() { return role; }
    public String getFullName() { return fullName; }
}
