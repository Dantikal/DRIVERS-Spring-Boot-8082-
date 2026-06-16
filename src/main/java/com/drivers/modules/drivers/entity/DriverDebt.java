package com.drivers.modules.drivers.entity;

import com.drivers.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "driver_debts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverDebt extends BaseEntity {

    @Column(name = "driver_id", nullable = false, unique = true)
    private UUID driverId;

    @Column(name = "total_debt", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalDebt = BigDecimal.ZERO;
}