package com.drivers.modules.returns.dto;

import com.drivers.modules.returns.entity.ReturnReason;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ReturnItemDto(
        UUID id,
        UUID productId,
        Integer qtyBoxes,
        Integer qtyPieces,
        ReturnReason reason,
        String photoUrl,
        Instant createdAt,
        Instant updatedAt
) {}
