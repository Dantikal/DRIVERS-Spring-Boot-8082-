package com.drivers.modules.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken
) {}
