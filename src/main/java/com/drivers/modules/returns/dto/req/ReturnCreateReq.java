package com.drivers.modules.returns.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record ReturnCreateReq(
        @NotNull(message = "ID водителя обязателен")
        UUID driverId,

        @NotNull(message = "Сумма возврата обязательна")
        @DecimalMin(value = "0.01", message = "Сумма возврата должна быть больше 0")
        BigDecimal totalAmount,

        @NotEmpty(message = "Список возвращенных товаров не может быть пустым")
        List<@Valid ReturnItemReq> items
) {}
