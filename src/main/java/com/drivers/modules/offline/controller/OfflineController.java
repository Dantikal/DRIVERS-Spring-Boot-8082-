package com.drivers.modules.offline.controller;

import com.drivers.modules.offline.dto.req.OfflineSyncRequest;
import com.drivers.modules.offline.dto.res.OfflineSyncResponse;
import com.drivers.modules.offline.service.OfflineService;
import com.drivers.shared.util.CurrentDriverId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/drivers/offline")
@RequiredArgsConstructor
public class OfflineController {

    private final OfflineService offlineService;

    @PostMapping("/sync")
    public ResponseEntity<OfflineSyncResponse> sync(
            @RequestBody OfflineSyncRequest request,
            @CurrentDriverId UUID driverId) {
        
        OfflineSyncResponse response = offlineService.syncOfflineQueue(request, driverId);
        return ResponseEntity.ok(response);
    }
}
