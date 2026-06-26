package com.drivers.offline;

import com.drivers.modules.offline.controller.OfflineController;
import com.drivers.modules.offline.dto.req.OfflineSyncRequest;
import com.drivers.modules.offline.dto.res.OfflineSyncResponse;
import com.drivers.modules.offline.service.OfflineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfflineControllerTest {

    @Mock
    private OfflineService offlineService;

    @InjectMocks
    private OfflineController offlineController;

    @Test
    void sync_ReturnsOkAndResponse() {
        UUID driverId = UUID.randomUUID();
        OfflineSyncRequest request = new OfflineSyncRequest(List.of());
        OfflineSyncResponse mockResponse = new OfflineSyncResponse(List.of());

        when(offlineService.syncOfflineQueue(request, driverId)).thenReturn(mockResponse);

        ResponseEntity<OfflineSyncResponse> response = offlineController.sync(request, driverId);

        verify(offlineService).syncOfflineQueue(request, driverId);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(mockResponse, response.getBody());
    }
}
