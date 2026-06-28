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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import com.drivers.modules.events.publisher.DriverEventPublisher;
import com.drivers.shared.dto.IdempotentResponse;
import com.drivers.shared.idempotency.IdempotencyHelper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private static final String TOPIC_ORDERS_NEW      = "orders:new";
    private static final String TOPIC_ORDERS_UPDATED  = "orders:updated";

    private final DriverOrderRepo orderRepo;
    private final DriverService driverService;
    private final DriverEventPublisher eventPublisher;
    private final IdempotencyHelper idempotencyHelper;

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDto> getOrders(Pageable pageable, UUID driverId, OrderStatus status) {
        Specification<DriverOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (driverId != null) {
                predicates.add(cb.equal(root.get("driverId"), driverId));
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
    public IdempotentResponse<OrderDto> createOrder(OrderCreateReq req, UUID driverId, String idempotencyKey) {
        Optional<DriverOrder> optionalOrder = orderRepo.findByIdempotencyKey(idempotencyKey);
        if (optionalOrder.isPresent()) {
            log.info("Idempotency hit: Returning existing order {}", optionalOrder.get().getId());
            return new IdempotentResponse<>(toDto(optionalOrder.get()), true);
        }

        driverService.getDriver(driverId);

        DriverOrder order = DriverOrder.builder()
                .driverId(driverId)
                .warehouseId(req.warehouseId())
                .status(OrderStatus.NEW)
                .requestedAt(Instant.now())
                .totalAmount(req.totalAmount())
                .comment(req.comment())
                .idempotencyKey(idempotencyKey)
                .items(new ArrayList<>())
                .build();

        req.items().forEach(itemReq -> order.getItems().add(toEntity(itemReq, order)));

        try {
            DriverOrder saved = idempotencyHelper.saveOrder(order);
            publishOrderEvent(saved, "ORDER_CREATED", TOPIC_ORDERS_NEW);
            log.info("Created new order: {} for driver: {}", saved.getId(), saved.getDriverId());
            return new IdempotentResponse<>(toDto(saved), false);

        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrency hit for idempotency key {}. Fetching order saved by another thread.", idempotencyKey);
            DriverOrder racedOrder = orderRepo.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new RuntimeException("Неожиданная ошибка параллельного выполнения"));
            return new IdempotentResponse<>(toDto(racedOrder), true);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getOrder(UUID id, UUID driverId) {
        DriverOrder order = getOrderById(id);
        if (!order.getDriverId().equals(driverId)) {
            throw new AccessDeniedException("Вы не имеете доступа к данному заказу");
        }
        return toDto(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getOrder(UUID id) {
        return toDto(getOrderById(id));
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

        DriverOrder saved = orderRepo.save(order);
        driverService.getDriver(order.getDriverId());
        // driverService.increaseDebt(order.getDriverId(), order.getTotalAmount());

        publishOrderEvent(saved, "ORDER_UPDATED", TOPIC_ORDERS_UPDATED);
        log.info("Зав. склада подтвердил выдачу заявки {}. Начислен долг водителю {}: {}",
                id, order.getDriverId(), order.getTotalAmount());
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
        publishOrderEvent(saved, "ORDER_UPDATED", TOPIC_ORDERS_UPDATED);
        log.info("Накладная {} скорректирована зав. склада", id);
        return toDto(saved);
    }

    @Override
    @Transactional
    public OrderDto rejectOrder(UUID id, OrderRejectReq req) {
        DriverOrder order = getOrderById(id);

        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.MODIFIED) {
            throw new IllegalStateException("Нельзя отклонить заявку в статусе: " + order.getStatus());
        }

        order.setStatus(OrderStatus.REJECTED);
        if (req != null && req.comment() != null) {
            order.setComment(req.comment());
        }

        DriverOrder saved = orderRepo.save(order);
        publishOrderEvent(saved, "ORDER_REJECTED", TOPIC_ORDERS_UPDATED);
        log.info("Заявка {} отклонена складом", id);
        return toDto(saved);
    }

    @Override
    @Transactional
    public OrderDto markDispatched(UUID id) {
        DriverOrder order = getOrderById(id);

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("В доставку можно передать только подтвержденную заявку. Текущий статус: " + order.getStatus());
        }

        order.setStatus(OrderStatus.DISPATCHED);
        driverService.increaseDebt(order.getDriverId(), order.getTotalAmount());
        DriverOrder saved = orderRepo.save(order);

        publishOrderEvent(saved, "ORDER_DISPATCHED", TOPIC_ORDERS_UPDATED);
        log.info("Заявка {} переведена в статус DISPATCHED (товар передан водителю)", id);

        return toDto(saved);
    }

    private DriverOrder getOrderById(UUID id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Заказ с ID: " + id + " не найден"));
    }

    private void updateOrderItems(DriverOrder order, List<OrderItemReq> newItemsReq) {
        Set<UUID> newProductIds = newItemsReq.stream()
                .map(OrderItemReq::productId)
                .collect(Collectors.toSet());

        order.getItems().removeIf(item -> !newProductIds.contains(item.getProductId()));

        for (OrderItemReq req : newItemsReq) {
            DriverOrderItem existingItem = order.getItems().stream()
                    .filter(item -> item.getProductId().equals(req.productId()))
                    .findFirst()
                    .orElse(null);

            if (existingItem != null) {
                existingItem.setRequestedQty(req.requestedQty());
                existingItem.setApprovedQty(req.approvedQty());
            } else {
                order.getItems().add(toEntity(req, order));
            }
        }
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
                .requestedAt(order.getRequestedAt() != null
                        ? order.getRequestedAt()
                        : null)
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
        DriverOrderEvent event = DriverOrderEvent.builder()
                .orderId(order.getId())
                .driverId(order.getDriverId())
                .warehouseId(order.getWarehouseId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .eventType(eventType)
                .timestamp(LocalDateTime.now().toString())
                .build();
        if (TOPIC_ORDERS_NEW.equals(topic)) {
            eventPublisher.publishOrderNew(event);
        } else {
            eventPublisher.publishOrderUpdated(event);
        }
    }
}