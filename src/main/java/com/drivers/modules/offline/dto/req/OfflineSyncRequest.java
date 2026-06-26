package com.drivers.modules.offline.dto.req;

import java.util.List;

public record OfflineSyncRequest(
        List<OfflineOperationReq> operations
) {
}
