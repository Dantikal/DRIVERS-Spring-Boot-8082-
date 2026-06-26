package com.drivers.drivers;

import com.drivers.modules.auth.entity.DriverAuth;
import com.drivers.modules.auth.repository.DriverAuthRepository;
import com.drivers.modules.drivers.dto.DriverDebtDto;
import com.drivers.modules.drivers.dto.DriverDto;
import com.drivers.modules.drivers.dto.req.DriverCreateReq;
import com.drivers.modules.drivers.dto.req.DriverUpdateReq;
import com.drivers.modules.drivers.entity.Driver;
import com.drivers.modules.drivers.entity.DriverDebt;
import com.drivers.modules.drivers.entity.DriverStatus;
import com.drivers.modules.drivers.mapper.DriverDebtMapper;
import com.drivers.modules.drivers.mapper.DriverMapper;
import com.drivers.modules.drivers.repository.DriverDebtRepository;
import com.drivers.modules.drivers.repository.DriverRepository;
import com.drivers.modules.drivers.repository.specification.DriverSpecification;
import com.drivers.modules.drivers.service.impl.DriverServiceImpl;
import com.drivers.shared.exception.ex.CarNumberAlreadyExistsException;
import com.drivers.shared.exception.ex.DriverNotFoundException;
import com.drivers.shared.exception.ex.NegativeDebtException;
import com.drivers.shared.exception.ex.PhoneAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DriverServiceTest {
    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DriverRepository driverRepository;
    @Mock
    private DriverAuthRepository driverAuthRepository;
    @Mock
    private DriverDebtRepository driverDebtRepository;

    @InjectMocks
    private DriverServiceImpl driverService;

    @Mock
    private DriverMapper driverMapper;
    @Mock
    private DriverDebtMapper driverDebtMapper;


    private Driver driver;
    private DriverAuth driverAuth;
    private DriverDebt driverDebt;
    private DriverDto driverDto;

    private UUID driverId;
    private UUID authId;
    private UUID debtId;

    private String fullName;
    private String phone;
    private String password;
    private String carNumber;
    private UUID warehouseId;
    private BigDecimal totalDebt;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        authId = UUID.randomUUID();
        debtId = UUID.randomUUID();

        fullName = "Айдоочу Айдоочубеков";
        phone = "+996707123456";
        password = "password";
        carNumber = "01 730 AZX";
        warehouseId = UUID.randomUUID();
        totalDebt = BigDecimal.ZERO;

        driver = Driver.builder()
                .fullName(fullName)
                .phone(phone)
                .carNumber(carNumber)
                .status(DriverStatus.ACTIVE)
                .warehouseId(warehouseId)
                .build();
        driver.id = driverId;

        driverAuth = DriverAuth.builder()
                .driverId(driver.getId())
                .phone(phone)
                .password(password)
                .build();
        driverAuth.id = authId;

        driverDebt = DriverDebt.builder()
                .driverId(driver.getId())
                .totalDebt(totalDebt)
                .build();
        driverDebt.id = debtId;

        driverDto = DriverDto.builder()
                .id(driver.id)
                .fullName(driver.getFullName())
                .phone(driver.getPhone())
                .status(DriverStatus.ACTIVE)
                .warehouseId(driver.getWarehouseId())
                .carNumber(driver.getCarNumber())
                .build();
    }

    @Test
    void createDriver_whenDriverCreated_thenDriverCreated() {
        DriverCreateReq req = DriverCreateReq.builder()
                .fullName(fullName)
                .phone(phone)
                .password(password)
                .carNumber(carNumber)
                .warehouseId(warehouseId)
                .build();

        when(driverRepository.save(any(Driver.class))).thenReturn(driver);
        when(driverAuthRepository.save(any(DriverAuth.class))).thenReturn(driverAuth);
        when(driverDebtRepository.save(any(DriverDebt.class))).thenReturn(driverDebt);
        when(driverMapper.toDto(driver)).thenReturn(driverDto);
        when(driverRepository.existsByPhone(any(String.class))).thenReturn(false);
        when(driverRepository.existsByCarNumber(any(String.class))).thenReturn(false);
        when(passwordEncoder.encode(any(String.class))).thenReturn(password);


        DriverDto res = driverService.createDriver(req);

        assertNotNull(res);
        assertEquals(res, driverDto);

        verify(driverRepository, times(1)).save(any(Driver.class));
        verify(driverAuthRepository, times(1)).save(any(DriverAuth.class));
        verify(driverDebtRepository, times(1)).save(any(DriverDebt.class));
    }

    @Test
    void createDriver_whenPhoneAlreadyExists_thenExceptionThrown() {
        DriverCreateReq req = DriverCreateReq.builder()
                .fullName(fullName)
                .phone(phone)
                .password(password)
                .carNumber(carNumber)
                .warehouseId(warehouseId)
                .build();
        when(driverRepository.existsByPhone(phone)).thenReturn(true);

        assertThrows(PhoneAlreadyExistsException.class, () -> driverService.createDriver(req));

        verify(driverRepository, times(1)).existsByPhone(phone);
        verify(driverRepository, never()).save(any(Driver.class));
    }

    @Test
    void createDriver_whenCarNumberAlreadyExists_thenExceptionThrown() {
        DriverCreateReq req = DriverCreateReq.builder()
                .fullName(fullName)
                .phone(phone)
                .password(password)
                .carNumber(carNumber)
                .warehouseId(warehouseId)
                .build();
        when(driverRepository.existsByCarNumber(carNumber)).thenReturn(true);

        assertThrows(CarNumberAlreadyExistsException.class, () -> driverService.createDriver(req));

        verify(driverRepository, times(1)).existsByCarNumber(carNumber);
        verify(driverRepository, never()).save(any(Driver.class));
    }

    @Test
    void updateDriver_whenDriverUpdated_thenDriverUpdated(){
        DriverUpdateReq req = DriverUpdateReq.builder()
                .fullName(fullName)
                .phone(phone)
                .carNumber(carNumber)
                .warehouseId(warehouseId)
                .build();

        when(driverRepository.findById(any(UUID.class))).thenReturn(java.util.Optional.of(driver));
        when(driverMapper.toDto(any(Driver.class))).thenReturn(driverDto);
        when(driverRepository.save(any(Driver.class))).thenReturn(driver);

        DriverDto res = driverService.updateDriver(driverId, req);

        assertNotNull(res);
        assertEquals(res, driverDto);

        verify(driverRepository, times(1)).findById(any(UUID.class));
        verify(driverMapper, times(1)).toDto(any(Driver.class));
        verify(driverRepository, times(1)).save(any(Driver.class));
    }

    @Test
    void getDriver_whenDriverExists_thenDriverReturned(){
        when(driverRepository.findById(any(UUID.class))).thenReturn(java.util.Optional.of(driver));
        when(driverMapper.toDto(any(Driver.class))).thenReturn(driverDto);

        DriverDto res = driverService.getDriver(driverId);

        assertNotNull(res);
        assertEquals(res, driverDto);

        verify(driverRepository, times(1)).findById(any(UUID.class));
        verify(driverMapper, times(1)).toDto(any(Driver.class));
    }

    @Test
    void getDriver_whenDriverDoesNotExist_thenThrowDriverNotFoundException() {
        UUID fakeDriverId = UUID.randomUUID();

        when(driverRepository.findById(fakeDriverId)).thenReturn(java.util.Optional.empty());

        DriverNotFoundException exception = assertThrows(
                DriverNotFoundException.class,
                () -> driverService.getDriver(fakeDriverId)
        );

        assertEquals("Водитель с ID: " + fakeDriverId + " не найден", exception.getMessage());

        verify(driverRepository, times(1)).findById(fakeDriverId);
        verifyNoInteractions(driverMapper); // Крутой метод Mockito для проверки чистоты
    }

    @Test
    void getAllDrivers_whenDriversExist_thenDriversReturned(){
        Pageable pageable = Pageable.ofSize(20);
        Page<Driver> driversPage = new PageImpl<>(java.util.List.of(driver));
        Specification<Driver> spec = DriverSpecification.hasStatus(DriverStatus.ACTIVE);
        when(driverRepository.findAll(any(Specification.class),any(Pageable.class))).thenReturn(driversPage);
        when(driverMapper.toDto(any(Driver.class))).thenReturn(driverDto);

        Page<DriverDto> res = driverService.getAllDrivers(pageable, DriverStatus.ACTIVE, null, null);

        assertNotNull(res);
        assertEquals(1, res.getContent().size());
        assertEquals(res.getContent().get(0), driverDto);

        verify(driverRepository, times(1)).findAll(any(Specification.class), eq(pageable));
        verify(driverMapper, times(1)).toDto(any(Driver.class));
    }

    @Test
    void increaseDebt_whenDebtIncreased_thenDebtIncreased(){
        BigDecimal debt = BigDecimal.valueOf(1000);

        when(driverDebtRepository.findByDriverIdForUpdate(any(UUID.class))).thenReturn(java.util.Optional.of(driverDebt));

        driverService.increaseDebt(driverId, debt);

        verify(driverDebtRepository, times(1)).findByDriverIdForUpdate(any(UUID.class));
        verify(driverDebtRepository, times(1)).save(any(DriverDebt.class));
    }

    @Test
    void decreaseDebt_whenDebtDecreased_thenDebtDecreased(){
        BigDecimal debt = BigDecimal.valueOf(1000);
        driverDebt.setTotalDebt(debt);

        when(driverDebtRepository.findByDriverIdForUpdate(any(UUID.class))).thenReturn(java.util.Optional.of(driverDebt));

        driverService.decreaseDebt(driverId, debt);

        assertEquals(BigDecimal.ZERO, driverDebt.getTotalDebt());

        verify(driverDebtRepository, times(1)).findByDriverIdForUpdate(any(UUID.class));
        verify(driverDebtRepository, times(1)).save(any(DriverDebt.class));
    }

    @Test
    void decreaseDebt_whenDebtDecreasedAndTotalDebtIsNegative_thenThrowException(){
        BigDecimal debt = BigDecimal.valueOf(1000);

        when(driverDebtRepository.findByDriverIdForUpdate(any(UUID.class))).thenReturn(java.util.Optional.of(driverDebt));

        assertThrows(NegativeDebtException.class, () -> driverService.decreaseDebt(driverId, debt));

        verify(driverDebtRepository, times(1)).findByDriverIdForUpdate(any(UUID.class));
        verifyNoInteractions(driverRepository);
    }

    @Test
    void getDriverDebt_Success_thenDriverDebtReturned(){
        DriverDebtDto driverDebtDto = DriverDebtDto.builder()
                .driverId(driverDebt.getDriverId())
                .totalDebt(driverDebt.getTotalDebt())
                .build();

        when(driverDebtRepository.findByDriverId(any(UUID.class))).thenReturn(java.util.Optional.of(driverDebt));
        when(driverDebtMapper.toDto(any(DriverDebt.class), any(String.class), any(String.class))).thenReturn(driverDebtDto);
        when(driverRepository.findById(any(UUID.class))).thenReturn(java.util.Optional.of(driver));


        DriverDebtDto res = driverService.getDriverDebt(driverId);

        assertNotNull(res);
        assertEquals(driverDebtDto, res);

        verify(driverDebtRepository, times(1)).findByDriverId(any(UUID.class));
        verify(driverDebtMapper, times(1)).toDto(any(DriverDebt.class), any(String.class), any(String.class));
        verify(driverRepository, times(1)).findById(any(UUID.class));
    }

    @Test
    void getAllDebts_whenDebtsExist_thenSortedPageReturned() {
        DriverDebtDto dd1 = DriverDebtDto.builder()
                .fullName(driver.getFullName())
                .driverId(driver.getId())
                .totalDebt(driverDebt.getTotalDebt())
                .carNumber(driver.getCarNumber())
                .updatedAt(driver.updatedAt)
                .build();
        DriverDebtDto dd2 = DriverDebtDto.builder()
                .fullName("Айдоочугүл Айдоочубековна")
                .driverId(UUID.randomUUID())
                .totalDebt(BigDecimal.valueOf(5000))
                .carNumber("01 001 KYZ")
                .updatedAt(Instant.now())
                .build();


        Page<DriverDebtDto> dds = new PageImpl<>(List.of(dd1, dd2));
        when(driverDebtRepository.findAllDriverDebts(any(Pageable.class), nullable(UUID.class), nullable(BigDecimal.class))).thenReturn(dds);

        Page<DriverDebtDto> allDebts = driverService.getAllDebts(PageRequest.of(0, 10), null, null);

        assertNotNull(allDebts);
        assertEquals(2, allDebts.getContent().size());

        verify(driverDebtRepository, times(1)).findAllDriverDebts(any(Pageable.class), nullable(UUID.class), nullable(BigDecimal.class));
    }



}
