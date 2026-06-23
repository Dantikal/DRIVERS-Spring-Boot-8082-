package com.drivers.modules.returns.dto.event;

import com.drivers.modules.returns.entity.ReturnReason;
import com.drivers.modules.returns.entity.ReturnStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record ReturnEvent(
        UUID returnId,
        UUID driverId,
        ReturnStatus status,
        BigDecimal totalAmount,
        String eventType,
        String timestamp,
        List<ReturnItemEvent> items
) {
    @Builder
    public record ReturnItemEvent(
            UUID productId,
            Integer qtyBoxes,
            Integer qtyPieces,
            ReturnReason reason
    ) {}
}