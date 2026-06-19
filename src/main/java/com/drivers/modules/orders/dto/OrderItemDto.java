package com.drivers.modules.orders.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record OrderItemDto(
        UUID id,
        UUID productId,
        Integer requestedQty,
        Integer approvedQty,
        Instant createdAt,
        Instant updatedAt
) {}
