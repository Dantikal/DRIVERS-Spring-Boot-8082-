package com.drivers.modules.orders.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record OrderModifyReq(
        @NotNull(message = "Сумма заявки обязательна")
        @DecimalMin(value = "0.01", message = "Сумма заявки должна быть больше 0")
        BigDecimal totalAmount,

        String comment,

        @NotEmpty(message = "Список товаров не может быть пустым")
        List<@Valid OrderItemReq> items
) {}
