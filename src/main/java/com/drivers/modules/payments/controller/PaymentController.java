package com.drivers.modules.payments.controller;

import com.drivers.modules.payments.dto.PaymentDto;
import com.drivers.modules.payments.dto.req.PaymentCreateReq;
import com.drivers.modules.payments.entity.PaymentMethod;
import com.drivers.modules.payments.service.PaymentService;
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
@RequestMapping({"/payments", "/api/payments", "/api/drivers/payments"})
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Платежи водителей")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "Получить список платежей")
    public Page<PaymentDto> getPayments(@PageableDefault(size = 20) Pageable pageable,
                                        @RequestParam(required = false) UUID driverId,
                                        @RequestParam(required = false) PaymentMethod paymentMethod) {
        return paymentService.getPayments(pageable, driverId, paymentMethod);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать платеж")
    public PaymentDto createPayment(@Valid @RequestBody PaymentCreateReq req) {
        return paymentService.createPayment(req);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить платеж по ID")
    public PaymentDto getPayment(@PathVariable UUID id) {
        return paymentService.getPayment(id);
    }
}
