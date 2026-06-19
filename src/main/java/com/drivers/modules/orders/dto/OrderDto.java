package com.drivers.modules.orders.dto;

import com.drivers.modules.orders.entity.OrderStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderDto(
        UUID id,
        UUID driverId,
        UUID warehouseId,
        OrderStatus status,
        LocalDateTime requestedAt,
        BigDecimal totalAmount,
        String comment,
        List<OrderItemDto> items,
        Instant createdAt,
        Instant updatedAt
) {}
