package com.drivers.modules.payments.dto.req;

import com.drivers.modules.payments.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record PaymentCreateReq(
        @NotNull(message = "ID водителя обязательно")
        UUID driverId,
        @NotNull(message = "Сумма платежа обязательна")
        @DecimalMin(value = "0.01", message = "Сумма платежа должна быть больше 0")
        BigDecimal amount,

        @NotNull(message = "Способ оплаты обязателен")
        PaymentMethod paymentMethod,

        String comment,

        @NotNull(message = "ID принявшего платеж обязателен")
        UUID receivedBy
) {}
