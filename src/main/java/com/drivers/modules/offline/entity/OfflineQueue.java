package com.drivers.modules.offline.entity;

import com.drivers.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "offline_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfflineQueue extends BaseEntity {

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "operation_type", nullable = false, length = 100)
    private String operationType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "created_offline_at", nullable = false)
    private LocalDateTime createdOfflineAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private QueueStatus status;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
}