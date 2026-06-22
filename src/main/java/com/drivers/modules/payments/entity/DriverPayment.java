package com.drivers.modules.payments.entity;

import com.drivers.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "driver_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverPayment extends BaseEntity {

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 50)
    private String idempotencyKey;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "received_by", nullable = false)
    private UUID receivedBy;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;
}