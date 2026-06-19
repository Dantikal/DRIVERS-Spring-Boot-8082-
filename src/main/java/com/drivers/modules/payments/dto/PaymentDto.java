package com.drivers.modules.payments.dto;

import com.drivers.modules.payments.entity.PaymentMethod;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record PaymentDto(
        UUID id,
        UUID driverId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String comment,
        UUID receivedBy,
        LocalDateTime paidAt,
        Instant createdAt,
        Instant updatedAt
) {}
