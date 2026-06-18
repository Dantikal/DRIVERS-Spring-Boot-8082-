package com.drivers.modules.auth.dto.req;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Номер телефона обязателен")
        String phone,
        @NotBlank(message = "Пароль обязателен")
        String password
) {}
