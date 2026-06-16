package com.drivers.modules.auth.entity;

import com.drivers.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "driver_auth")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverAuth extends BaseEntity {

    @Column(name = "driver_id", nullable = false, unique = true)
    private UUID driverId;

    @Column(name = "phone", nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;
}