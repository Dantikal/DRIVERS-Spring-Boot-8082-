package com.drivers.modules.drivers.service;

import com.drivers.modules.drivers.dto.req.DriverCreateReq;
import com.drivers.modules.drivers.dto.DriverDebtDto;
import com.drivers.modules.drivers.dto.DriverDto;
import com.drivers.modules.drivers.dto.req.DriverUpdateReq;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface DriverService {
    DriverDto createDriver(DriverCreateReq driverCreateReq);
    DriverDto updateDriver(UUID id, DriverUpdateReq dto);
    DriverDto getDriver(UUID id);
    Page<DriverDto> getAllDrivers(Pageable pageable);
    void increaseDebt(UUID driverId, BigDecimal amount);
    void decreaseDebt(UUID driverId, BigDecimal amount);
    DriverDebtDto getDriverDebt(UUID driverId);
    Page<DriverDebtDto> getAllDebts(Pageable pageable);

}
