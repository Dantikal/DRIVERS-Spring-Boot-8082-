package com.drivers.modules.payments.service;

import com.drivers.modules.payments.dto.PaymentDto;
import com.drivers.modules.payments.dto.req.PaymentCreateReq;
import com.drivers.modules.payments.entity.PaymentMethod;
import com.drivers.shared.dto.IdempotentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PaymentService {
    Page<PaymentDto> getPayments(Pageable pageable, UUID driverId, PaymentMethod paymentMethod);

    IdempotentResponse<PaymentDto> createPayment(PaymentCreateReq req, String idempotencyKey);

    PaymentDto getPayment(UUID id, UUID driverId);
}