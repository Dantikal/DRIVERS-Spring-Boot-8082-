package com.drivers.modules.orders.controller;

import com.drivers.modules.orders.dto.OrderDto;
import com.drivers.modules.orders.dto.req.OrderCreateReq;
import com.drivers.modules.orders.entity.OrderStatus;
import com.drivers.modules.orders.service.OrderService;
import com.drivers.shared.util.CurrentDriverId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/drivers/me/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "Driver — Orders", description = "Управление заявками из мобильного приложения водителя")
public class DriverOrderSelfController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Создать новую заявку на товар ")
    public ResponseEntity<OrderDto> createOrder(@Valid @RequestBody OrderCreateReq req,
                                      @CurrentDriverId UUID driverId,
                                      @Parameter(in = ParameterIn.HEADER, name = "Idempotency-Key", description = "Idempotency-Key to prevent duplicates")
                                @RequestHeader(name = "Idempotency-Key") String idempotencyKey) {
        OrderDto res = orderService.createOrder(req, driverId, idempotencyKey);

        if(orderService.checkIfThisOrderWasAlreadyCreated(res)){
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header("Idempotency-Replayed", "true")
                    .body(res);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping()
    @Operation(summary = "Получить список СВОИХ заявок с пагинацией и фильтром по статусу")
    public Page<OrderDto> getMyOrders(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) OrderStatus status,
            @CurrentDriverId UUID driverId) {
        return orderService.getOrders(pageable, driverId, status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить детали своей заявки по ID")
    public OrderDto getMyOrder(@PathVariable UUID id, @CurrentDriverId UUID driverId) {
        return orderService.getOrder(id, driverId);
    }
}
