package com.drivers.modules.confirmation.service.impl;

import com.drivers.modules.confirmation.service.ConfirmationService;
import com.drivers.modules.events.dto.DriverOrderEvent;
import com.drivers.modules.orders.entity.DriverOrder;
import com.drivers.modules.orders.entity.OrderStatus;
import com.drivers.modules.orders.repository.DriverOrderRepo;
import com.drivers.shared.exception.ex.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.drivers.modules.events.publisher.DriverEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConfirmationServiceImpl implements ConfirmationService {

    private final DriverOrderRepo orderRepo;
    private final DriverEventPublisher eventPublisher;

    @Override
    @Transactional
    public void confirmationReceipt(UUID dispatchId, UUID driverId) {
        DriverOrder order = orderRepo.findById(dispatchId)
                .orElseThrow(() -> new OrderNotFoundException("Заказ с ID: " + dispatchId + " не найден"));

        if (!order.getDriverId().equals(driverId)) {
            throw new AccessDeniedException("Вы не имеете доступа к данному заказу");
        }
        if(order.getStatus() != OrderStatus.DISPATCHED) {
            throw new IllegalStateException("Статус заказа не является отправленным");
        }

        log.info("Водитель {} подтвердил получение заказа {}", driverId, dispatchId);

        DriverOrderEvent event = DriverOrderEvent.builder()
                .orderId(order.getId())
                .driverId(order.getDriverId())
                .warehouseId(order.getWarehouseId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .eventType("RECEIPT_CONFIRMED")
                .timestamp(LocalDateTime.now().toString())
                .build();
        eventPublisher.publishOrderUpdated(event);
    }
}
