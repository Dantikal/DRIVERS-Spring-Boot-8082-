package com.drivers.modules.drivers.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record DriverDebtDto(
        UUID id,
        UUID driverId,
        BigDecimal totalDebt
) {}
