package com.drivers.modules.offline.dto.req;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record OfflineOperationReq(
        UUID localId,
        String operationType,
        Map<String, Object> payload,
        LocalDateTime createdOfflineAt
) {
}
