package com.drivers.modules.drivers.repository;

import com.drivers.modules.drivers.entity.DriverDebt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverDebtRepository extends JpaRepository<DriverDebt, UUID> {
    Optional<DriverDebt> findByDriverId(UUID driverId);
}
