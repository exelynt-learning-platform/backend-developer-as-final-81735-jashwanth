package com.example.booking.dto;

public record LoginResponse(
        String token,
        String tokenType,
        String username,
        String role
) {}
