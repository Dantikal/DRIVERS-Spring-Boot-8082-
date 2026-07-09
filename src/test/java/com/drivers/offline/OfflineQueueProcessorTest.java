package com.drivers.offline;

import com.drivers.modules.offline.entity.OfflineQueue;
import com.drivers.modules.offline.entity.QueueStatus;
import com.drivers.modules.offline.repository.OfflineQueueRepo;
import com.drivers.modules.offline.service.impl.OfflineQueueProcessor;
import com.drivers.modules.orders.dto.req.OrderCreateReq;
import com.drivers.modules.orders.service.OrderService;
import com.drivers.modules.returns.dto.req.ReturnCreateReq;
import com.drivers.modules.returns.service.ReturnService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfflineQueueProcessorTest {

    @Mock
    private OfflineQueueRepo offlineQueueRepo;
    @Mock
    private OrderService orderService;
    @Mock
    private ReturnService returnService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OfflineQueueProcessor offlineQueueProcessor;

    private UUID driverId;
    private UUID queueId;
    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        queueId = UUID.randomUUID();
        payload = Map.of("dummy", "data");
    }

    @Test
    void processOfflineQueue_WithCreateOrder_ShouldProcessSuccessfully() {
        OfflineQueue task = OfflineQueue.builder()
                
                .driverId(driverId)
                .operationType("CREATE_ORDER")
                .payload(payload)
                .createdOfflineAt(LocalDateTime.now())
                .status(QueueStatus.PENDING)
                .retryCount(0)
                .build();
        task.id = queueId;

        when(offlineQueueRepo.findByStatusOrderByCreatedOfflineAtAsc(QueueStatus.PENDING))
                .thenReturn(List.of(task));

        OrderCreateReq mockReq = OrderCreateReq.builder().build();
        task.id = queueId;
        when(objectMapper.convertValue(payload, OrderCreateReq.class)).thenReturn(mockReq);

        offlineQueueProcessor.processOfflineQueue();

        verify(orderService, times(1)).createOrder(eq(mockReq), eq(driverId), eq(queueId.toString()));
        verify(offlineQueueRepo, times(1)).save(task);
        assertEquals(QueueStatus.PROCESSED, task.getStatus());
    }

    @Test
    void processOfflineQueue_WithCreateReturn_ShouldProcessSuccessfully() {
        OfflineQueue task = OfflineQueue.builder()
                
                .driverId(driverId)
                .operationType("CREATE_RETURN")
                .payload(payload)
                .createdOfflineAt(LocalDateTime.now())
                .status(QueueStatus.PENDING)
                .retryCount(0)
                .build();
        task.id = queueId;

        when(offlineQueueRepo.findByStatusOrderByCreatedOfflineAtAsc(QueueStatus.PENDING))
                .thenReturn(List.of(task));

        ReturnCreateReq mockReq = ReturnCreateReq.builder().build();
        task.id = queueId;
        when(objectMapper.convertValue(payload, ReturnCreateReq.class)).thenReturn(mockReq);

        offlineQueueProcessor.processOfflineQueue();

        verify(returnService, times(1)).createReturn(eq(mockReq), eq(driverId), eq(queueId.toString()));
        verify(offlineQueueRepo, times(1)).save(task);
        assertEquals(QueueStatus.PROCESSED, task.getStatus());
    }

    @Test
    void processOfflineQueue_WhenServiceThrowsException_ShouldIncrementRetryCount() {
        OfflineQueue task = OfflineQueue.builder()
                
                .driverId(driverId)
                .operationType("CREATE_ORDER")
                .payload(payload)
                .createdOfflineAt(LocalDateTime.now())
                .status(QueueStatus.PENDING)
                .retryCount(0)
                .build();
        task.id = queueId;

        when(offlineQueueRepo.findByStatusOrderByCreatedOfflineAtAsc(QueueStatus.PENDING))
                .thenReturn(List.of(task));

        when(objectMapper.convertValue(any(), eq(OrderCreateReq.class))).thenThrow(new RuntimeException("Test Exception"));

        offlineQueueProcessor.processOfflineQueue();

        verify(offlineQueueRepo, times(1)).save(task);
        assertEquals(QueueStatus.PENDING, task.getStatus());
        assertEquals(1, task.getRetryCount());
    }

    @Test
    void processOfflineQueue_WhenExceedsRetryCount_ShouldMarkAsFailed() {
        OfflineQueue task = OfflineQueue.builder()
                
                .driverId(driverId)
                .operationType("CREATE_ORDER")
                .payload(payload)
                .createdOfflineAt(LocalDateTime.now())
                .status(QueueStatus.PENDING)
                .retryCount(2)
                .build();
        task.id = queueId;

        when(offlineQueueRepo.findByStatusOrderByCreatedOfflineAtAsc(QueueStatus.PENDING))
                .thenReturn(List.of(task));

        when(objectMapper.convertValue(any(), eq(OrderCreateReq.class))).thenThrow(new RuntimeException("Test Exception"));

        offlineQueueProcessor.processOfflineQueue();

        verify(offlineQueueRepo, times(1)).save(task);
        assertEquals(QueueStatus.FAILED, task.getStatus());
        assertEquals(3, task.getRetryCount());
    }

    @Test
    void processOfflineQueue_WithUnknownOperation_ShouldFail() {
        OfflineQueue task = OfflineQueue.builder()
                
                .driverId(driverId)
                .operationType("UNKNOWN_OP")
                .payload(payload)
                .createdOfflineAt(LocalDateTime.now())
                .status(QueueStatus.PENDING)
                .retryCount(0)
                .build();
        task.id = queueId;

        when(offlineQueueRepo.findByStatusOrderByCreatedOfflineAtAsc(QueueStatus.PENDING))
                .thenReturn(List.of(task));

        offlineQueueProcessor.processOfflineQueue();

        verify(offlineQueueRepo, times(1)).save(task);
        assertEquals(QueueStatus.PENDING, task.getStatus());
        assertEquals(1, task.getRetryCount());
    }
}
