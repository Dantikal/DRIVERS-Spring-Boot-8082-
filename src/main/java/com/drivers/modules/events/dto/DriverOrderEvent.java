package com.drivers.modules.events.dto;

import com.drivers.modules.orders.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record DriverOrderEvent(UUID orderId,
                               UUID driverId,
                               UUID warehouseId,
                               OrderStatus status,
                               BigDecimal totalAmount,
                               String eventType,
                               String timestamp)
        implements Serializable {}
