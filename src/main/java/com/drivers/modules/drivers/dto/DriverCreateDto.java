package com.drivers.modules.drivers.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DriverCreateDto(
        @NotBlank(message = "Full name is required")
        String fullName,
        @NotBlank(message = "Car number is required")
        String carNumber,
        @NotBlank(message = "Phone number is required")
        String phone,
        @NotBlank(message = "Password is required")
        @Min(value=8, message = "Password must be at least 8 characters long")
        String password,
        @NotNull(message = "Warehouse id is required")
        UUID warehouseId

) {}
