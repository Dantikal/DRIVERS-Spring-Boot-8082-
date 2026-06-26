package com.drivers.modules.events.listener;

import com.drivers.modules.events.dto.DriverOrderEvent;
import com.drivers.modules.orders.entity.DriverOrder;
import com.drivers.modules.orders.entity.OrderStatus;
import com.drivers.modules.orders.repository.DriverOrderRepo;
import com.drivers.modules.drivers.service.DriverService;
import com.drivers.shared.exception.ex.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DispatchCreatedListener implements MessageListener {

    private final DriverOrderRepo orderRepo;
    private final DriverService driverService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            DriverOrderEvent event = (DriverOrderEvent) redisTemplate
                    .getValueSerializer()
                    .deserialize(message.getBody());

            if (event == null) {
                log.warn("DispatchCreatedListener: received null event, skipping");
                return;
            }

            log.info("DispatchCreatedListener: received event '{}' for order {}",
                    event.eventType(), event.orderId());

            handleDispatchCreated(event);

        } catch (Exception e) {
            log.error("DispatchCreatedListener: failed to process message: {}", e.getMessage(), e);
        }
    }

    private void handleDispatchCreated(DriverOrderEvent event) {
        UUID orderId = event.orderId();

        DriverOrder order = orderRepo.findById(orderId).orElse(null);

        if (order == null) {
            log.warn("DispatchCreatedListener: order {} not found, skipping", orderId);
            return;
        }

        if (order.getStatus() == OrderStatus.DISPATCHED) {
            log.info("DispatchCreatedListener: order {} already DISPATCHED, skipping (idempotency)", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            log.warn("DispatchCreatedListener: order {} is in status {}, expected CONFIRMED — skipping",
                    orderId, order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.DISPATCHED);
        orderRepo.save(order);

        driverService.increaseDebt(order.getDriverId(), order.getTotalAmount());

        log.info("DispatchCreatedListener: order {} marked DISPATCHED, debt increased for driver {} by {}",
                orderId, order.getDriverId(), order.getTotalAmount());
    }
}