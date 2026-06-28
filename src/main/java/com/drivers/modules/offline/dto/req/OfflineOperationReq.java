package com.drivers.modules.offline.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record OfflineOperationReq(
        @NotNull(message = "Локальный ID обязателен")
        UUID localId,
        
        @NotBlank(message = "Тип операции обязателен")
        String operationType,
        
        @NotNull(message = "Payload не может быть null")
        Map<String, Object> payload,
        
        @NotNull(message = "Время создания обязательно")
        LocalDateTime createdOfflineAt
) {
}
