package com.drivers.modules.confirmation.service.impl;

import com.drivers.modules.confirmation.service.ConfirmationService;
import com.drivers.modules.events.dto.DriverOrderEvent;
import com.drivers.modules.orders.entity.DriverOrder;
import com.drivers.modules.orders.repository.DriverOrderRepo;
import com.drivers.shared.exception.ex.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConfirmationServiceImpl implements ConfirmationService {

    private final DriverOrderRepo orderRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TOPIC_ORDERS_UPDATED = "orders:updated";

    @Override
    public void confirmationReceipt(UUID dispatchId, UUID driverId) {
        DriverOrder order = orderRepo.findById(dispatchId)
                .orElseThrow(() -> new OrderNotFoundException("Заказ с ID: " + dispatchId + " не найден"));

        if (!order.getDriverId().equals(driverId)) {
            throw new AccessDeniedException("Вы не имеете доступа к данному заказу");
        }

        log.info("Водитель {} подтвердил получение заказа {}", driverId, dispatchId);

        try {
            DriverOrderEvent event = DriverOrderEvent.builder()
                    .orderId(order.getId())
                    .driverId(order.getDriverId())
                    .warehouseId(order.getWarehouseId())
                    .status(order.getStatus())
                    .totalAmount(order.getTotalAmount())
                    .eventType("RECEIPT_CONFIRMED")
                    .timestamp(LocalDateTime.now().toString())
                    .build();
            redisTemplate.convertAndSend(TOPIC_ORDERS_UPDATED, event);
            log.info("Redis event 'RECEIPT_CONFIRMED' → topic '{}' for order {}", TOPIC_ORDERS_UPDATED, order.getId());
        } catch (Exception e) {
            log.error("Не удалось опубликовать событие в Redis для подтверждения заказа {}: {}", order.getId(), e.getMessage());
        }
    }
}
