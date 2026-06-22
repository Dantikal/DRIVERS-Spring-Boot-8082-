package com.drivers.modules.payments.dto.event;

import com.drivers.modules.payments.entity.PaymentMethod;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record PaymentEvent(
        UUID paymentId,
        UUID driverId,
        BigDecimal amount,
        PaymentMethod method,
        String eventType,
        String timestamp
) {}
