package com.drivers.modules.offline.service;

import com.drivers.modules.offline.dto.req.OfflineSyncRequest;
import com.drivers.modules.offline.dto.res.OfflineSyncResponse;

import java.util.UUID;

public interface OfflineService {
    OfflineSyncResponse syncOfflineQueue(OfflineSyncRequest req, UUID driverId);
}
