package com.drivers.modules.payments.service.impl;

import com.drivers.modules.drivers.entity.Driver;
import com.drivers.modules.drivers.service.DriverService;
import com.drivers.modules.payments.dto.PaymentDto;
import com.drivers.modules.payments.dto.event.PaymentEvent;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final static String TOPIC_PAYMENTS_RECEIVED = "payments:received";

    private final DriverPaymentRepo paymentRepo;
    private final DriverService driverService;
    private final RedisTemplate<String, Object> redisTemplate;

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
    public PaymentDto createPayment(PaymentCreateReq req, String idempotencyKey) {
            Optional<DriverPayment> optionalPayment = paymentRepo.findByIdempotencyKey(idempotencyKey);
            if(optionalPayment.isPresent()){
                log.info("Idempotency hit: Returning existing payment {}", optionalPayment.get().getId());
                return toDto(optionalPayment.get());
            }
        driverService.decreaseDebt(req.driverId(), req.amount());

        DriverPayment payment = DriverPayment.builder()
                .driverId(req.driverId())
                .amount(req.amount())
                .paymentMethod(req.paymentMethod())
                .comment(req.comment())
                .idempotencyKey(idempotencyKey)
                .receivedBy(req.receivedBy())
                .paidAt(Instant.now())
                .build();

        DriverPayment saved = paymentRepo.save(payment);
        log.info("Created payment {} for driver {}", saved.getId(), saved.getDriverId());
        publishPaymentReceivedEvent(saved);
        return toDto(saved);
    }

    @Override
    public boolean checkIfThisPaymentWasAlreadyCreated(PaymentDto res) {
        if (res.createdAt() == null) {
            return false;
        }

        return res.createdAt()
                .isBefore(Instant.now().minusSeconds(2));
    }

    private void publishPaymentReceivedEvent(DriverPayment payment) {
        try {
            PaymentEvent event = PaymentEvent.builder()
                    .paymentId(payment.getId())
                    .driverId(payment.getDriverId())
                    .amount(payment.getAmount())
                    .method(payment.getPaymentMethod())
                    .eventType("PAYMENT_RECEIVED")
                    .timestamp(Instant.now().toString())
                    .build();

            redisTemplate.convertAndSend(TOPIC_PAYMENTS_RECEIVED, event);
            log.info("Published payment received event to topic '{}' for payment{}",
                    TOPIC_PAYMENTS_RECEIVED, payment.getId());

        } catch (Exception e) {
            log.error("Couldn't publish payment event: {}: {}", payment.getId(), e.getMessage());
        }
    }


    @Override
    @Transactional(readOnly = true)
    public PaymentDto getPayment(UUID id, UUID driverId) {
        if(driverId == null){
            return toDto(paymentRepo.findById(id).orElseThrow(()->new PaymentNotFoundException("Платёж с ID: " + id + " не найден")));
        }
        return toDto(paymentRepo.findByIdAndDriverId(id, driverId)
                .orElseThrow(()-> new PaymentNotFoundException
                        ("Платёж с ID: " + id + " и driverId: " + driverId + " не найден")));
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
