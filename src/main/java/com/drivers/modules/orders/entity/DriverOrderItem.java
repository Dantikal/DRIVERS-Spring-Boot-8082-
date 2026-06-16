package com.drivers.modules.orders.entity;

import com.drivers.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "driver_order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverOrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private DriverOrder order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "approved_qty")
    private Integer approvedQty;
}