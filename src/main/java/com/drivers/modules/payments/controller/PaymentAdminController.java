package com.drivers.modules.payments.controller;

import com.drivers.modules.payments.dto.PaymentDto;
import com.drivers.modules.payments.dto.req.PaymentCreateReq;
import com.drivers.modules.payments.entity.PaymentMethod;
import com.drivers.modules.payments.service.PaymentService;
import com.drivers.shared.dto.IdempotentResponse;
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
@RequestMapping("/api/drivers/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
@Tag(name = "Warehouse — Payments", description = "Прием оплат от водителей")
public class PaymentAdminController {

    private final PaymentService        // 1. Проверка идемпотентности
 paymentService;

    @PostMapping()
    @Operation(summary = "Провести платеж (списать долг водителя)")
    public ResponseEntity<PaymentDto> createPayment(@Valid @RequestBody PaymentCreateReq req,
                                                    @Parameter(in = ParameterIn.HEADER, name = "Idempotency-Key", description = "Idempotency-Key to prevent duplicates")
                                                    @RequestHeader(name = "Idempotency-Key") String idempotencyKey) {

        IdempotentResponse<PaymentDto> response = paymentService.createPayment(req, idempotencyKey);

        if(response.isReplayed()) {
            return ResponseEntity.status(HttpStatus.OK)
                    .header("Idempotency-Replayed", "true")
                    .body(response.data());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response.data());
    }

    @GetMapping
    @Operation(summary = "История всех платежей")
    public Page<PaymentDto> getAllPayments(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) PaymentMethod paymentMethod) {
        return paymentService.getPayments(pageable, driverId, paymentMethod);
    }
}