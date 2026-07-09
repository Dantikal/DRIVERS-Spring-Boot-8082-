package com.drivers.integration;

import com.drivers.modules.offline.dto.req.OfflineOperationReq;
import com.drivers.modules.offline.dto.req.OfflineSyncRequest;
import com.drivers.modules.offline.dto.res.OfflineSyncResponse;
import com.drivers.modules.offline.entity.OfflineQueue;
import com.drivers.modules.offline.entity.QueueStatus;
import com.drivers.modules.offline.repository.OfflineQueueRepo;
import com.drivers.modules.offline.service.impl.OfflineQueueProcessor;
import com.drivers.modules.orders.dto.req.OrderCreateReq;
import com.drivers.modules.orders.service.OrderService;
import com.drivers.shared.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class OfflineSyncIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private OfflineQueueRepo offlineQueueRepo;

    @Autowired
    private OfflineQueueProcessor offlineQueueProcessor;

    @MockitoBean
    private OrderService orderService;

    private HttpHeaders authHeaders;
    private UUID driverId;

    @BeforeEach
    void setUp() {
        offlineQueueRepo.deleteAll();
        
        driverId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "driver_phone",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_DRIVER"))
        );
        String token = jwtUtil.generateToken(authentication, driverId, UUID.randomUUID());

        authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("Offline Sync E2E: Sync via API -> DB -> Processor -> Target Service")
    void executeFullOfflineSyncFlow() throws Exception {
        
        // 1. Send sync request via API
        UUID localId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
            "warehouseId", UUID.randomUUID().toString(),
            "totalAmount", 1500.00,
            "items", List.of(Map.of("productId", UUID.randomUUID().toString(), "requestedQty", 5))
        );

        OfflineOperationReq operation = new OfflineOperationReq(
            localId,
            "CREATE_ORDER",
            payload,
            LocalDateTime.now().minusHours(1)
        );
        
        OfflineSyncRequest syncReq = new OfflineSyncRequest(List.of(operation));

        HttpEntity<String> httpReq = new HttpEntity<>(objectMapper.writeValueAsString(syncReq), authHeaders);
        ResponseEntity<OfflineSyncResponse> res = restTemplate.postForEntity("/api/drivers/offline/sync", httpReq, OfflineSyncResponse.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().results().size());
        assertEquals(localId, res.getBody().results().get(0).localId());
        assertEquals(true, res.getBody().results().get(0).success());

        // 2. Verify it is saved in DB as PENDING
        List<OfflineQueue> pendingQueues = offlineQueueRepo.findByStatusOrderByCreatedOfflineAtAsc(QueueStatus.PENDING);
        assertEquals(1, pendingQueues.size());
        OfflineQueue savedQueue = pendingQueues.get(0);
        assertEquals(driverId, savedQueue.getDriverId());
        assertEquals("CREATE_ORDER", savedQueue.getOperationType());

        // 3. Manually trigger the processor
        offlineQueueProcessor.processOfflineQueue();

        // 4. Verify that OrderService was called
        verify(orderService, times(1)).createOrder(any(OrderCreateReq.class), eq(driverId), eq(savedQueue.getId().toString()));

        // 5. Verify that the DB record was updated to PROCESSED
        OfflineQueue processedQueue = offlineQueueRepo.findById(savedQueue.getId()).orElseThrow();
        assertEquals(QueueStatus.PROCESSED, processedQueue.getStatus());
    }
}
