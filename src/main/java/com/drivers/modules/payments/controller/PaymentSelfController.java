package com.drivers.modules.payments.controller;

import com.drivers.modules.payments.dto.PaymentDto;
import com.drivers.modules.payments.entity.PaymentMethod;
import com.drivers.modules.payments.service.PaymentService;
import com.drivers.shared.util.CurrentDriverId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/drivers/me/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "Driver - Payments",
        description = "Платежи водителей")
public class PaymentSelfController {

    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "Получить список платежей")
    public Page<PaymentDto> getPayments(@PageableDefault(size = 20) Pageable pageable,
                                        @CurrentDriverId UUID driverId,
                                        @RequestParam(required = false) PaymentMethod paymentMethod) {
        return paymentService.getPayments(pageable, driverId, paymentMethod);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить платеж по ID")
    public PaymentDto getPayment(@PathVariable UUID id,
                                 @CurrentDriverId UUID driverId) {
        return paymentService.getPayment(id, driverId);
    }
}
