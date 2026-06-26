package com.drivers.modules.offline.dto.res;

import java.util.UUID;

public record OfflineSyncResult(
        UUID localId,
        boolean success,
        String message
) {
}
