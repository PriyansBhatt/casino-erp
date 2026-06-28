package com.casino.casinoerp.dto;

public class LoginResponse {

    private String message;
    private String username;
    private String status;
    private String role;
    private String token;

    public LoginResponse(String message, String username, String status, String role, String token) {
        this.message = message;
        this.username = username;
        this.status = status;
        this.role = role;
        this.token = token;
    }

    public String getMessage() { return message; }

    public String getUsername() { return username; }

    public String getStatus() { return status; }

    public String getRole() { return role; }

    public String getToken() {
        return token;
    }
}