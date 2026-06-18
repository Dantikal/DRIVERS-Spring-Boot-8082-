package com.drivers.modules.drivers.dto.req;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record DriverCreateReq(
        @NotBlank(message = "ФИО обязательно для заполнения")
        String fullName,

        @NotBlank(message = "Гос. номер машины обязателен")
        String carNumber,

        @NotBlank(message = "Номер телефона обязателен")
        @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Неверный формат номера телефона")
        String phone,

        @NotBlank(message = "Пароль обязателен")
        @Size(min = 8, message = "Пароль должен содержать минимум 8 символов")
        String password,

        @NotNull(message = "ID склада обязателен")
        UUID warehouseId
) {}