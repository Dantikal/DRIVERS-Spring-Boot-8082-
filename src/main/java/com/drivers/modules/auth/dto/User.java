package com.drivers.modules.auth.dto;

import com.drivers.modules.drivers.entity.DriverStatus;
import lombok.Builder;

import java.util.UUID;

@Builder
public record User(
        UUID id,
        String fullName,
        String phone,
        String carNumber,
        UUID warehouseId,
        DriverStatus status
){}
