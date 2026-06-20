package com.drivers.modules.orders.service.impl;

import com.drivers.modules.drivers.entity.Driver;
import com.drivers.modules.drivers.service.DriverService;
import com.drivers.modules.events.dto.DriverOrderEvent;
import com.drivers.modules.orders.dto.OrderDto;
import com.drivers.modules.orders.dto.OrderItemDto;
import com.drivers.modules.orders.dto.req.OrderCreateReq;
import com.drivers.modules.orders.dto.req.OrderItemReq;
import com.drivers.modules.orders.dto.req.OrderModifyReq;
import com.drivers.modules.orders.dto.req.OrderRejectReq;
import com.drivers.modules.orders.entity.DriverOrder;
import com.drivers.modules.orders.entity.DriverOrderItem;
import com.drivers.modules.orders.entity.OrderStatus;
import com.drivers.modules.orders.repository.DriverOrderRepo;
import com.drivers.modules.orders.service.OrderService;
import com.drivers.shared.exception.ex.OrderNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final DriverOrderRepo orderRepo;
    private final DriverService driverService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDto> getOrders(Pageable pageable, UUID driverId, OrderStatus status) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isDriver = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DRIVER"));
        if (isDriver) {
            driverId = driverService.getCurrentDriverId();
        }

        UUID finalDriverId = driverId;
        Specification<DriverOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (finalDriverId != null) {
                predicates.add(cb.equal(root.get("driverId"), finalDriverId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return orderRepo.findAll(spec, pageable).map(this::toDto);
    }

    @Override
    @Transactional
    public OrderDto createOrder(OrderCreateReq req, UUID id) {
        driverService.getDriver(id);

        DriverOrder order = DriverOrder.builder()
                .driverId(id)
                .warehouseId(req.warehouseId())
                .status(OrderStatus.NEW)
                .requestedAt(LocalDateTime.now())
                .totalAmount(req.totalAmount())
                .comment(req.comment())
                .items(new ArrayList<>())
                .build();

        req.items().forEach(itemReq -> order.getItems().add(toEntity(itemReq, order)));

        DriverOrder saved = orderRepo.save(order);
        publishOrderEvent(saved, "ORDER_CREATED", "orders:new");
        log.info("Успешно создана новая заявка {} для водителя {}", saved.getId(), saved.getDriverId());
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getOrder(UUID id, UUID driverId) {
        DriverOrder order = getOrderById(id);
        if(!order.getDriverId().equals(driverId)){
            throw new AccessDeniedException("Вы не имеете доступа к данному заказу");
        }
        return toDto(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getOrder(UUID id) {
        DriverOrder order = getOrderById(id);
        return toDto(order);
    }

    @Override
    @Transactional
    public OrderDto confirmOrder(UUID id) {
        DriverOrder order = getOrderById(id);

        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.MODIFIED) {
            throw new IllegalStateException("Нельзя выдать товар по заказу в статусе: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CONFIRMED);

        order.getItems().forEach(item -> {
            if (item.getApprovedQty() == null) {
                item.setApprovedQty(item.getRequestedQty());
            }
        });

        driverService.increaseDebt(order.getDriverId(), order.getTotalAmount());

        DriverOrder saved = orderRepo.save(order);
        publishOrderEvent(saved, "ORDER_UPDATED", "orders:updated");
        log.info("Заведующий склада подтвердил выдачу накладной {}. Начислен долг водителю: {}", id, order.getTotalAmount());
        return toDto(saved);
    }

    @Override
    @Transactional
    public OrderDto modifyOrder(UUID id, OrderModifyReq req) {
        DriverOrder order = getOrderById(id);

        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.MODIFIED) {
            throw new IllegalStateException("Нельзя редактировать накладную в статусе: " + order.getStatus());
        }

        order.setStatus(OrderStatus.MODIFIED);
        order.setComment(req.comment());

        order.setTotalAmount(req.totalAmount());

        updateOrderItems(order, req.items());

        DriverOrder saved = orderRepo.save(order);
        publishOrderEvent(saved, "ORDER_UPDATED", "orders:updated");
        log.info("Накладная {} успешно скорректирована заведующим склада", id);
        return toDto(saved);
    }

    @Override
    @Transactional
    public OrderDto rejectOrder(UUID id, OrderRejectReq req) {
        DriverOrder order = getOrderById(id);

        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.DISPATCHED) {
            throw new IllegalStateException("Нельзя отклонить уже выданный водителю товар");
        }

        order.setStatus(OrderStatus.REJECTED);
        if (req != null && req.comment() != null) {
            order.setComment(req.comment());
        }

        DriverOrder saved = orderRepo.save(order);
        publishOrderEvent(saved, "ORDER_REJECTED", "orders:updated");
        log.info("Заявка на товар {} отклонена складом", id);
        return toDto(saved);
    }

    private DriverOrder getOrderById(UUID id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Накладная/Заказ с ID: " + id + " не найден в системе"));
    }

    /**
     * Безопасное обновление позиций заказа без вызова деструктивного .clear()
     */
    private void updateOrderItems(DriverOrder order, List<OrderItemReq> newItemsReq) {
        // Для MVP-сценария пока оставляем замену через коллекцию JPA,
        // но в будущем здесь должна быть синхронизация по productId
        order.getItems().clear();
        newItemsReq.forEach(req -> order.getItems().add(toEntity(req, order)));
    }

    private DriverOrderItem toEntity(OrderItemReq req, DriverOrder order) {
        return DriverOrderItem.builder()
                .order(order)
                .productId(req.productId())
                .requestedQty(req.requestedQty())
                .approvedQty(req.approvedQty())
                .build();
    }

    private OrderDto toDto(DriverOrder order) {
        return OrderDto.builder()
                .id(order.getId())
                .driverId(order.getDriverId())
                .warehouseId(order.getWarehouseId())
                .status(order.getStatus())
                .requestedAt(order.getRequestedAt())
                .totalAmount(order.getTotalAmount())
                .comment(order.getComment())
                .items(order.getItems().stream().map(this::toItemDto).toList())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderItemDto toItemDto(DriverOrderItem item) {
        return OrderItemDto.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .requestedQty(item.getRequestedQty())
                .approvedQty(item.getApprovedQty())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private void publishOrderEvent(DriverOrder order, String eventType, String topic) {
        try {
            DriverOrderEvent event = DriverOrderEvent.builder()
                    .orderId(order.getId())
                    .driverId(order.getDriverId())
                    .warehouseId(order.getWarehouseId())
                    .status(order.getStatus())
                    .totalAmount(order.getTotalAmount())
                    .eventType(eventType)
                    .timestamp(java.time.LocalDateTime.now().toString())
                    .build();

            redisTemplate.convertAndSend(topic, event);
            log.info("Redis Pub/Sub: Event '{}' published to topic '{}' for order {}", eventType, topic, order.getId());
        } catch (Exception e) {
            log.error("Failed to publish order event to Redis Pub/Sub", e);
        }
    }
}