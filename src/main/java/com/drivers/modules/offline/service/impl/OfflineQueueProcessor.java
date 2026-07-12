package com.drivers.modules.offline.service.impl;

import com.drivers.modules.offline.entity.OfflineQueue;
import com.drivers.modules.offline.entity.QueueStatus;
import com.drivers.modules.offline.repository.OfflineQueueRepo;
import com.drivers.modules.orders.dto.req.OrderCreateReq;
import com.drivers.modules.orders.service.OrderService;
import com.drivers.modules.returns.dto.req.ReturnCreateReq;
import com.drivers.modules.returns.service.ReturnService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfflineQueueProcessor {

    private final OfflineQueueRepo offlineQueueRepo;
    private final OrderService orderService;
    private final ReturnService returnService;
    private final ObjectMapper objectMapper;

    /**
     * Executes every 10 seconds. Finds PENDING offline operations and processes them.
     */
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void processOfflineQueue() {
        List<OfflineQueue> pendingTasks = offlineQueueRepo.findByStatusOrderByCreatedOfflineAtAsc(QueueStatus.PENDING);
        
        if (pendingTasks.isEmpty()) {
            return;
        }

        log.info("Found {} pending offline operations to process", pendingTasks.size());

        for (OfflineQueue task : pendingTasks) {
            try {
                processTask(task);
                task.setStatus(QueueStatus.PROCESSED);
                offlineQueueRepo.save(task);
                log.info("Successfully processed offline task {} (type: {})", task.getId(), task.getOperationType());
            } catch (Exception e) {
                log.error("Failed to process offline task {}: {}", task.getId(), e.getMessage(), e);
                task.setRetryCount(task.getRetryCount() + 1);
                
                if (task.getRetryCount() >= 3) {
                    task.setStatus(QueueStatus.FAILED);
                    log.error("Task {} marked as FAILED after {} retries", task.getId(), task.getRetryCount());
                }
                
                offlineQueueRepo.save(task);
            }
        }
    }

    private void processTask(OfflineQueue task) {
        String idempotencyKey = task.getId().toString();

        switch (task.getOperationType()) {
            case "CREATE_ORDER" -> {
                OrderCreateReq orderReq = objectMapper.convertValue(task.getPayload(), OrderCreateReq.class);
                orderService.createOrder(orderReq, task.getDriverId(), idempotencyKey);
            }
            case "MODIFY_ORDER" -> {
                UUID orderId = UUID.fromString(task.getPayload().get("orderId").toString());
                com.drivers.modules.orders.dto.req.OrderModifyReq modifyReq = objectMapper.convertValue(task.getPayload(), com.drivers.modules.orders.dto.req.OrderModifyReq.class);
                orderService.modifyMyOrder(orderId, task.getDriverId(), modifyReq);
            }
            case "CREATE_RETURN" -> {
                ReturnCreateReq returnReq = objectMapper.convertValue(task.getPayload(), ReturnCreateReq.class);
                returnService.createReturn(returnReq, task.getDriverId(), idempotencyKey);
            }
            case "DELETE_ORDER" -> {
                UUID orderId = UUID.fromString(task.getPayload().get("orderId").toString());
                orderService.deleteMyOrder(orderId, task.getDriverId());
            }
            default -> throw new IllegalArgumentException("Unsupported operation type: " + task.getOperationType());
        }
    }
}
