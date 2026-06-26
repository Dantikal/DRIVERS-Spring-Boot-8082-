package com.drivers.modules.offline.dto.res;

import java.util.List;

public record OfflineSyncResponse(
        List<OfflineSyncResult> results
) {
}
