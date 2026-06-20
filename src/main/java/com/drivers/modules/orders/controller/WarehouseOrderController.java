package com.drivers.modules.orders.controller;

import com.drivers.modules.orders.dto.OrderDto;
import com.drivers.modules.orders.dto.req.OrderModifyReq;
import com.drivers.modules.orders.dto.req.OrderRejectReq;
import com.drivers.modules.orders.entity.OrderStatus;
import com.drivers.modules.orders.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/warehouse/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
@Tag(name = "Warehouse — Orders Management", description = "Панель управления накладными/заявками для завскладом")
public class WarehouseOrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "Посмотреть список всех заявок в системе (с фильтрами)")
    public Page<OrderDto> getAllOrders(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) OrderStatus status) {
        return orderService.getOrders(pageable, driverId, status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить любую заявку по ID")
    public OrderDto getOrder(@PathVariable UUID id) {
        return orderService.getOrder(id);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Подтвердить выдачу товара по заявке (начисляет долг водителю)")
    public OrderDto confirmOrder(@PathVariable UUID id) {
        return orderService.confirmOrder(id);
    }

    @PostMapping("/{id}/modify")
    @Operation(summary = "Скорректировать позиции в заявке перед выдачей")
    public OrderDto modifyOrder(@PathVariable UUID id, @Valid @RequestBody OrderModifyReq req) {
        return orderService.modifyOrder(id, req);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Отклонить заявку водителя")
    public OrderDto rejectOrder(@PathVariable UUID id, @RequestBody(required = false) OrderRejectReq req) {
        return orderService.rejectOrder(id, req);
    }
}