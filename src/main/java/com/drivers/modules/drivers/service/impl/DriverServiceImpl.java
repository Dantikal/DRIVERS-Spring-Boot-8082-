package com.drivers.modules.drivers.service.impl;

import com.drivers.modules.auth.entity.DriverAuth;
import com.drivers.modules.auth.repository.DriverAuthRepository;
import com.drivers.modules.drivers.dto.req.DriverCreateReq;
import com.drivers.modules.drivers.dto.DriverDebtDto;
import com.drivers.modules.drivers.dto.DriverDto;
import com.drivers.modules.drivers.dto.req.DriverUpdateReq;
import com.drivers.modules.drivers.entity.Driver;
import com.drivers.modules.drivers.entity.DriverDebt;
import com.drivers.modules.drivers.entity.DriverStatus;
import com.drivers.modules.drivers.mapper.DriverDebtMapper;
import com.drivers.modules.drivers.mapper.DriverMapper;
import com.drivers.modules.drivers.repository.DriverDebtRepository;
import com.drivers.modules.drivers.repository.DriverRepository;
import com.drivers.modules.drivers.service.DriverService;
import com.drivers.shared.exception.DriverNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverServiceImpl implements DriverService {
    private final DriverMapper driverMapper;
    private final DriverDebtMapper driverDebtMapper;

    private final DriverRepository driverRepository;
    private final DriverAuthRepository driverAuthRepository;
    private final DriverDebtRepository driverDebtRepository;

    @Override
    @Transactional
    public DriverDto createDriver(DriverCreateReq driverCreateReq) {
        Driver driverObject = Driver.builder()
                .fullName(driverCreateReq.fullName())
                .phone(driverCreateReq.phone())
                .carNumber(driverCreateReq.carNumber())
                .warehouseId(driverCreateReq.warehouseId())
                .status(DriverStatus.ACTIVE)
                .build();
        Driver driver = driverRepository.save(driverObject);
        DriverAuth driverAuth = DriverAuth.builder()
                .phone(driverCreateReq.phone())
                .password(driverCreateReq.password())
                .driverId(driver.getId())
                .build();
        DriverDebt driverDebt = DriverDebt.builder()
                .driverId(driver.getId())
                .totalDebt(BigDecimal.ZERO)
                .build();
        driverAuthRepository.save(driverAuth);
        driverDebtRepository.save(driverDebt);

        log.info("Initialized driver with id {} and set zero as his debt", driver.getId());

        return driverMapper.toDto(driver);
    }

    @Override
    @Transactional
    public DriverDto updateDriver(UUID id, DriverUpdateReq req) {
        Driver driver = getDriverById(id);
        log.info("Driver with id {} updated", id);
        return updateDriverData(req, driver);
    }
    private DriverDto updateDriverData(DriverUpdateReq req, Driver driver){
        driver.setFullName(req.fullName());
        driver.setPhone(req.phone());
        driver.setStatus(req.status());
        driver.setCarNumber(req.carNumber());
        driver.setWarehouseId(req.warehouseId());
        return driverMapper.toDto(driverRepository.save(driver));
    }

    @Override
    @Transactional(readOnly = true)
    public DriverDto getDriver(UUID id) {
        Driver driver = getDriverById(id);
        return driverMapper.toDto(driver);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DriverDto> getAllDrivers(Pageable pageable) {
        return driverRepository.findAll(pageable).map(driverMapper::toDto);
    }

    @Override
    @Transactional
    public void increaseDebt(UUID driverId, BigDecimal amount) {
        DriverDebt driverDebt = getDriverDebtById(driverId);
        driverDebt.setTotalDebt(driverDebt.getTotalDebt().add(amount));
        driverDebtRepository.save(driverDebt);
    }


    @Override
    @Transactional
    public void decreaseDebt(UUID driverId, BigDecimal amount) {
        DriverDebt driverDebt = getDriverDebtById(driverId);
        driverDebt.setTotalDebt(driverDebt.getTotalDebt().subtract(amount));
        driverDebtRepository.save(driverDebt);
    }

    @Override
    @Transactional(readOnly = true)
    public DriverDebtDto getDriverDebt(UUID driverId) {
        if(!driverRepository.existsById(driverId)){
            throw new DriverNotFoundException("Водитель с таким ID: " + driverId + " не найден");
        }
        return driverDebtMapper.toDto(getDriverDebtById(driverId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DriverDebtDto> getAllDebts(Pageable pageable) {
        Pageable sortedByDesc = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "totalDebt")
        );
        return driverDebtRepository.findAll(sortedByDesc).map(driverDebtMapper::toDto);
    }

    private Driver getDriverById(UUID id){
        return driverRepository.findById(id).orElseThrow(()->new DriverNotFoundException("Водитель с ID: " + id + " не найден"));
    }
    private DriverDebt getDriverDebtById(UUID driverId) {
        return driverDebtRepository.findByDriverId(driverId).orElseThrow(()->new DriverNotFoundException("Водитель с таким ID: " + driverId + " не найден"));
    }}
