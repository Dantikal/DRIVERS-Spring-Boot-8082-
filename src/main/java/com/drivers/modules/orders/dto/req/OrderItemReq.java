package com.drivers.modules.orders.dto.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

import java.util.UUID;

@Builder
public record OrderItemReq(
        @NotNull(message = "ID товара обязателен")
        UUID productId,

        @NotNull(message = "Запрошенное количество обязательно")
        @Positive(message = "Запрошенное количество должно быть больше 0")
        Integer requestedQty,

        @PositiveOrZero(message = "Подтвержденное количество не может быть отрицательным")
        Integer approvedQty
) {}
