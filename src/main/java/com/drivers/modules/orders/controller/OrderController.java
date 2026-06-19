package com.drivers.modules.orders.controller;

import com.drivers.modules.orders.dto.OrderDto;
import com.drivers.modules.orders.dto.req.OrderCreateReq;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/orders", "/api/orders", "/api/drivers/orders"})
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Заявки водителей")
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "Получить список заявок")
    public Page<OrderDto> getOrders(@PageableDefault(size = 20) Pageable pageable,
                                    @RequestParam(required = false) UUID driverId,
                                    @RequestParam(required = false) OrderStatus status) {
        return orderService.getOrders(pageable, driverId, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать заявку")
    public OrderDto createOrder(@Valid @RequestBody OrderCreateReq req) {
        return orderService.createOrder(req);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить заявку по ID")
    public OrderDto getOrder(@PathVariable UUID id) {
        return orderService.getOrder(id);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Подтвердить заявку")
    public OrderDto confirmOrder(@PathVariable UUID id) {
        return orderService.confirmOrder(id);
    }

    @PostMapping("/{id}/modify")
    @Operation(summary = "Изменить заявку")
    public OrderDto modifyOrder(@PathVariable UUID id, @Valid @RequestBody OrderModifyReq req) {
        return orderService.modifyOrder(id, req);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Отклонить заявку")
    public OrderDto rejectOrder(@PathVariable UUID id, @RequestBody(required = false) OrderRejectReq req) {
        return orderService.rejectOrder(id, req);
    }
}
