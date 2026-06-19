package com.drivers.modules.payments.service.impl;

import com.drivers.modules.drivers.service.DriverService;
import com.drivers.modules.payments.dto.PaymentDto;
import com.drivers.modules.payments.dto.req.PaymentCreateReq;
import com.drivers.modules.payments.entity.DriverPayment;
import com.drivers.modules.payments.entity.PaymentMethod;
import com.drivers.modules.payments.repository.DriverPaymentRepo;
import com.drivers.modules.payments.service.PaymentService;
import com.drivers.shared.exception.ex.PaymentNotFoundException;
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
public class PaymentServiceImpl implements PaymentService {

    private final DriverPaymentRepo paymentRepo;
    private final DriverService driverService;

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentDto> getPayments(Pageable pageable, UUID driverId, PaymentMethod paymentMethod) {
        Specification<DriverPayment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (driverId != null) {
                predicates.add(cb.equal(root.get("driverId"), driverId));
            }
            if (paymentMethod != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), paymentMethod));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return paymentRepo.findAll(spec, pageable).map(this::toDto);
    }

    @Override
    @Transactional
    public PaymentDto createPayment(PaymentCreateReq req) {
        driverService.decreaseDebt(req.driverId(), req.amount());

        DriverPayment payment = DriverPayment.builder()
                .driverId(req.driverId())
                .amount(req.amount())
                .paymentMethod(req.paymentMethod())
                .comment(req.comment())
                .receivedBy(req.receivedBy())
                .paidAt(LocalDateTime.now())
                .build();

        DriverPayment saved = paymentRepo.save(payment);
        log.info("Created payment {} for driver {}", saved.getId(), saved.getDriverId());
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentDto getPayment(UUID id) {
        return toDto(getPaymentById(id));
    }

    private DriverPayment getPaymentById(UUID id) {
        return paymentRepo.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Платеж с ID: " + id + " не найден"));
    }

    private PaymentDto toDto(DriverPayment payment) {
        return PaymentDto.builder()
                .id(payment.getId())
                .driverId(payment.getDriverId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .comment(payment.getComment())
                .receivedBy(payment.getReceivedBy())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
