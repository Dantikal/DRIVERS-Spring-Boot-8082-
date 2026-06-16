package com.drivers.modules.returns.entity;

import com.drivers.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "return_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false)
    private ReturnRequest returnRequest;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "qty_boxes", nullable = false)
    private Integer qtyBoxes;

    @Column(name = "qty_pieces", nullable = false)
    private Integer qtyPieces;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private ReturnReason reason;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;
}