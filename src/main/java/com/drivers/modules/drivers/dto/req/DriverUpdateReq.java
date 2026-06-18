package com.drivers.modules.drivers.dto.req;

import com.drivers.modules.drivers.entity.DriverStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

import java.util.UUID;

@Builder
public record DriverUpdateReq(
        @NotBlank(message = "ФИО обязательно для заполнения")
        String fullName,
        @NotBlank(message = "Гос. номер машины обязателен")
        String carNumber,
        @NotBlank(message = "Номер телефона обязателен")
        @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Неверный формат телефона")
        String phone,
        @NotNull(message = "ID склада обязателен")
        UUID warehouseId,
        @NotNull(message = "Статус обязателен")
        DriverStatus status
) {}
