package com.drivers.modules.drivers.dto;

import com.drivers.modules.drivers.entity.DriverStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record DriverDto(
        UUID id,
        String fullName,
        String carNumber,
        String phone,
        UUID warehouseId,
        DriverStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
