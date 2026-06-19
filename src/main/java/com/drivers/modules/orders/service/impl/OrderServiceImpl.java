package com.drivers.modules.orders.service.impl;

import com.drivers.modules.drivers.service.DriverService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public OrderDto createOrder(OrderCreateReq req) {
        driverService.getDriver(req.driverId());

        DriverOrder order = DriverOrder.builder()
                .driverId(req.driverId())
                .warehouseId(req.warehouseId())
                .status(OrderStatus.NEW)
                .requestedAt(LocalDateTime.now())
                .totalAmount(req.totalAmount())
                .comment(req.comment())
                .items(new ArrayList<>())
                .build();

        replaceItems(order, req.items());
        DriverOrder saved = orderRepo.save(order);
        log.info("Created order {} for driver {}", saved.getId(), saved.getDriverId());
        return toDto(saved);
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
        order.setStatus(OrderStatus.CONFIRMED);
        order.getItems().forEach(item -> {
            if (item.getApprovedQty() == null) {
                item.setApprovedQty(item.getRequestedQty());
            }
        });

        DriverOrder saved = orderRepo.save(order);
        log.info("Confirmed order {}", id);
        return toDto(saved);
    }

    @Override
    @Transactional
    public OrderDto modifyOrder(UUID id, OrderModifyReq req) {
        DriverOrder order = getOrderById(id);
        order.setStatus(OrderStatus.MODIFIED);
        order.setTotalAmount(req.totalAmount());
        order.setComment(req.comment());
        replaceItems(order, req.items());

        DriverOrder saved = orderRepo.save(order);
        log.info("Modified order {}", id);
        return toDto(saved);
    }

    @Override
    @Transactional
    public OrderDto rejectOrder(UUID id, OrderRejectReq req) {
        DriverOrder order = getOrderById(id);
        order.setStatus(OrderStatus.REJECTED);
        if (req != null && req.comment() != null) {
            order.setComment(req.comment());
        }

        DriverOrder saved = orderRepo.save(order);
        log.info("Rejected order {}", id);
        return toDto(saved);
    }

    private DriverOrder getOrderById(UUID id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Заявка с ID: " + id + " не найдена"));
    }

    private void replaceItems(DriverOrder order, List<OrderItemReq> items) {
        order.getItems().clear();
        items.forEach(req -> order.getItems().add(toEntity(req, order)));
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
}
