package com.drivers.offline;

import com.drivers.modules.offline.dto.req.OfflineOperationReq;
import com.drivers.modules.offline.dto.req.OfflineSyncRequest;
import com.drivers.modules.offline.dto.res.OfflineSyncResponse;
import com.drivers.modules.offline.entity.OfflineQueue;
import com.drivers.modules.offline.repository.OfflineQueueRepo;
import com.drivers.modules.offline.service.impl.OfflineServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfflineServiceImplTest {

    @Mock
    private OfflineQueueRepo offlineQueueRepo;

    @InjectMocks
    private OfflineServiceImpl offlineService;

    @Test
    void syncOfflineQueue_Success() {
        UUID driverId = UUID.randomUUID();
        UUID localId = UUID.randomUUID();

        OfflineOperationReq req = new OfflineOperationReq(
                localId, "SYNC_ORDER", Map.of("key", "value"), LocalDateTime.now());
        OfflineSyncRequest request = new OfflineSyncRequest(List.of(req));

        when(offlineQueueRepo.save(any(OfflineQueue.class))).thenReturn(new OfflineQueue());

        OfflineSyncResponse response = offlineService.syncOfflineQueue(request, driverId);

        assertEquals(1, response.results().size());
        assertTrue(response.results().get(0).success());
        assertEquals(localId, response.results().get(0).localId());

        verify(offlineQueueRepo, times(1)).save(any(OfflineQueue.class));
    }

    @Test
    void syncOfflineQueue_Failure_CaughtAndReturnedAsFalse() {
        UUID driverId = UUID.randomUUID();
        UUID localId = UUID.randomUUID();

        OfflineOperationReq req = new OfflineOperationReq(
                localId, "SYNC_ORDER", Map.of("key", "value"), LocalDateTime.now());
        OfflineSyncRequest request = new OfflineSyncRequest(List.of(req));

        when(offlineQueueRepo.save(any(OfflineQueue.class))).thenThrow(new RuntimeException("DB Error"));

        OfflineSyncResponse response = offlineService.syncOfflineQueue(request, driverId);

        assertEquals(1, response.results().size());
        assertFalse(response.results().get(0).success());
        assertEquals("DB Error", response.results().get(0).message());

        verify(offlineQueueRepo, times(1)).save(any(OfflineQueue.class));
    }
}
