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
import com.drivers.modules.drivers.repository.specification.DriverSpecification;
import com.drivers.modules.drivers.service.DriverService;
import com.drivers.shared.exception.ex.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.Optional;
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

    private final PasswordEncoder passwordEncoder;

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
        try {
            Driver driver = driverRepository.saveAndFlush(driverObject);
            DriverAuth driverAuth = DriverAuth.builder()
                    .phone(driverCreateReq.phone())
                    .password(passwordEncoder.encode(driverCreateReq.password()))
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

        } catch (DataIntegrityViolationException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("ukbsce3om1aqn0lw0er0lejgxmd") || msg.contains("uk_drivers_phone")) {
                throw new PhoneAlreadyExistsException("Номер телефона уже зарегистрирован");
            }
            if (msg.contains("uk_drivers_car_number")) {
                throw new CarNumberAlreadyExistsException("Номер автомобиля уже зарегистрирован");
            }
            throw e; // re-throw if it's a different constraint
        }
    }

    @Override
    @Transactional
    public DriverDto updateDriver(UUID id, DriverUpdateReq req) {
        Driver driver = getDriverById(id);
        DriverDto res = updateDriverData(req, driver);
        log.info("Driver with id {} updated", id);
        return res;
    }
    private DriverDto updateDriverData(DriverUpdateReq req, Driver driver){
        if(driverRepository.existsByPhone(req.phone()) && !driver.getPhone().equals(req.phone())){
            throw new PhoneAlreadyExistsException("Номер телефона уже зарегистрирован");
        }
        if(driverRepository.existsByCarNumber(req.carNumber()) && !driver.getCarNumber().equals(req.carNumber())){
            throw new CarNumberAlreadyExistsException("Номер автомобиля уже используется другим водителем");
        }
        driver.setFullName(req.fullName());
        driver.setPhone(req.phone());
        driver.setCarNumber(req.carNumber());
        driver.setWarehouseId(req.warehouseId());
        driver.setStatus(req.status());

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
    public Page<DriverDto> getAllDrivers(Pageable pageable,
                                         DriverStatus status,
                                         UUID warehouseId,
                                         String search) {
        Specification<Driver> spec = DriverSpecification.hasStatus(status)
                .and(DriverSpecification.hasWarehouseId(warehouseId))
                .and(DriverSpecification.search(search));
        log.info("Searching for drivers with status: {}, warehouseId: {}, search: {}", status, warehouseId, search);
        Page<DriverDto> res =  driverRepository.findAll(spec, pageable).map(driverMapper::toDto);
        return res;
    }

    @Override
    @Transactional
    public void increaseDebt(UUID driverId, BigDecimal amount) {
        DriverDebt driverDebt = getDriverDebtForUpdate(driverId);
        driverDebt.setTotalDebt(driverDebt.getTotalDebt().add(amount));
        driverDebtRepository.save(driverDebt);
    }

    private DriverDebt getDriverDebtForUpdate(UUID driverId) {
        return driverDebtRepository.findByDriverIdForUpdate(driverId).orElseThrow(
                ()->new DriverDebtNotFoundException("Долг не найден"));
    }

    @Override
    @Transactional
    public void decreaseDebt(UUID driverId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма погашения долга должна быть больше нуля");
        }
        DriverDebt driverDebt = getDriverDebtForUpdate(driverId);
        BigDecimal newDebt = driverDebt.getTotalDebt().subtract(amount);
        if (newDebt.compareTo(BigDecimal.ZERO) < 0) {
            throw new NegativeDebtException("Сумма оплаты превышает текущий долг. Долг не может быть негативным");
        }

        driverDebt.setTotalDebt(newDebt);
        driverDebtRepository.save(driverDebt);
    }

    @Override
    @Transactional(readOnly = true)
    public DriverDebtDto getDriverDebt(UUID driverId) {
        Driver driver = getDriverById(driverId);

        return driverDebtMapper.toDto(getDriverDebtById(driverId), driver.getFullName(), driver.getCarNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DriverDebtDto> getAllDebts(Pageable pageable,
                                           UUID warehouseId,
                                           BigDecimal minDebt) {

        return driverDebtRepository.findAllDriverDebts(pageable, warehouseId, minDebt);
    }

    @Override
    public UUID getCurrentDriverId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            return (UUID) attrs.getRequest().getAttribute("X-Current-Driver-Id");
        }
        return null;
    }

    private Driver getDriverById(UUID id){
        return driverRepository.findById(id).orElseThrow(()->new DriverNotFoundException("Водитель с ID: " + id + " не найден"));
    }
    private DriverDebt getDriverDebtById(UUID driverId) {
        return driverDebtRepository.findByDriverId(driverId).orElseThrow(()->new DriverDebtNotFoundException("Водитель с таким ID: " + driverId + " не найден"));
    }
}
