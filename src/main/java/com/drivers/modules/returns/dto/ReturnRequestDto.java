package com.drivers.modules.returns.dto;

import com.drivers.modules.returns.entity.ReturnStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record ReturnRequestDto(
        UUID id,
        UUID driverId,
        LocalDateTime returnedAt,
        BigDecimal totalAmount,
        ReturnStatus status,
        List<ReturnItemDto> items,
        Instant createdAt,
        Instant updatedAt
) {}
