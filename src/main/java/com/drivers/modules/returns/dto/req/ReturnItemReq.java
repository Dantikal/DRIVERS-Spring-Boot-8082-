package com.drivers.modules.returns.dto.req;

import com.drivers.modules.returns.entity.ReturnReason;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ReturnItemReq(
        @NotNull(message = "ID товара обязателен")
        UUID productId,

        @NotNull(message = "Количество коробок обязательно")
        @PositiveOrZero(message = "Количество коробок не может быть отрицательным")
        Integer qtyBoxes,

        @NotNull(message = "Количество штук обязательно")
        @PositiveOrZero(message = "Количество штук не может быть отрицательным")
        Integer qtyPieces,

        @NotNull(message = "Причина возврата обязательна")
        ReturnReason reason,

        String photoUrl
) {
    @AssertTrue(message = "Нужно указать количество коробок или штук больше 0")
    public boolean hasQuantity() {
        return (qtyBoxes != null && qtyBoxes > 0) || (qtyPieces != null && qtyPieces > 0);
    }
}
