package com.drivers.modules.offline.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OfflineSyncRequest(
        @NotEmpty(message = "Список операций не может быть пустым")
        @Valid
        List<OfflineOperationReq> operations
) {
}
