package com.drivers.modules.drivers.repository;

import com.drivers.modules.drivers.dto.DriverDebtDto;
import com.drivers.modules.drivers.entity.DriverDebt;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverDebtRepository extends JpaRepository<DriverDebt, UUID> {

    @Query("""
        SELECT new com.drivers.modules.drivers.dto.DriverDebtDto(
        d.id,
        d.fullName,
        d.carNumber,
        dd.totalDebt,
        dd.updatedAt
        )
        FROM DriverDebt dd
        JOIN Driver d ON d.id = dd.driverId
        WHERE (:warehouseId IS NULL OR d.warehouseId = :warehouseId)
        AND(:minDebt IS NULL OR dd.totalDebt >= :minDebt)
        
""")
    Page<DriverDebtDto> findAllDriverDebts(Pageable pageable, UUID warehouseId, BigDecimal minDebt);
    Optional<DriverDebt> findByDriverId(UUID driverId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DriverDebt d WHERE d.driverId = :driverId")
    Optional<DriverDebt> findByDriverIdForUpdate(UUID driverId);
}
