package com.drivers.modules.offline.service.impl;

import com.drivers.modules.offline.dto.req.OfflineOperationReq;
import com.drivers.modules.offline.dto.req.OfflineSyncRequest;
import com.drivers.modules.offline.dto.res.OfflineSyncResponse;
import com.drivers.modules.offline.dto.res.OfflineSyncResult;
import com.drivers.modules.offline.entity.OfflineQueue;
import com.drivers.modules.offline.entity.QueueStatus;
import com.drivers.modules.offline.repository.OfflineQueueRepo;
import com.drivers.modules.offline.service.OfflineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfflineServiceImpl implements OfflineService {

    private final OfflineQueueRepo offlineQueueRepo;

    @Override
    @Transactional
    public OfflineSyncResponse syncOfflineQueue(OfflineSyncRequest req, UUID driverId) {
        List<OfflineSyncResult> results = new ArrayList<>();

        for (OfflineOperationReq operation : req.operations()) {
            try {
                OfflineQueue queueEntity = OfflineQueue.builder()
                        .driverId(driverId)
                        .operationType(operation.operationType())
                        .payload(operation.payload())
                        .createdOfflineAt(operation.createdOfflineAt())
                        .status(QueueStatus.PENDING)
                        .retryCount(0)
                        .build();

                offlineQueueRepo.save(queueEntity);
                
                results.add(new OfflineSyncResult(operation.localId(), true, "Saved to offline queue"));
            } catch (Exception e) {
                log.error("Failed to save offline operation for driver {}: {}", driverId, e.getMessage(), e);
                results.add(new OfflineSyncResult(operation.localId(), false, e.getMessage()));
            }
        }

        return new OfflineSyncResponse(results);
    }
}
