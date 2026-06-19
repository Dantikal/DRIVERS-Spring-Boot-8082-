package com.drivers.modules.drivers.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record DriverDebtDto(
        UUID driverId,
        String fullName,
        String carNumber,
        BigDecimal totalDebt,
        Instant updatedAt
) {}
