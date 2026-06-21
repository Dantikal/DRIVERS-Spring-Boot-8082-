package com.drivers.modules.orders.service;

import com.drivers.modules.orders.dto.OrderDto;
import com.drivers.modules.orders.dto.req.OrderCreateReq;
import com.drivers.modules.orders.dto.req.OrderModifyReq;
import com.drivers.modules.orders.dto.req.OrderRejectReq;
import com.drivers.modules.orders.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {
    Page<OrderDto> getOrders(Pageable pageable, UUID driverId, OrderStatus status);
    OrderDto createOrder(OrderCreateReq req, UUID id);
    OrderDto getOrder(UUID id, UUID driverId);
    OrderDto getOrder(UUID id);
    OrderDto confirmOrder(UUID id);
    OrderDto modifyOrder(UUID id, OrderModifyReq req);
    OrderDto rejectOrder(UUID id, OrderRejectReq req);
    OrderDto markDispatched(UUID id);
}
