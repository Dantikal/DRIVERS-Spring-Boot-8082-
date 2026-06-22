package com.drivers.modules.payments.repository;

import com.drivers.modules.payments.entity.DriverPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverPaymentRepo extends JpaRepository<DriverPayment, UUID>, JpaSpecificationExecutor<DriverPayment> {
    Optional<DriverPayment> findByIdAndDriverId(UUID id, UUID driverId);
    Optional<DriverPayment> findByIdempotencyKey(String idempotencyKey);
}
