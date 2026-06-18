package com.drivers.auth;

import com.drivers.modules.auth.dto.req.LoginRequest;
import com.drivers.modules.auth.dto.res.LoginResponse;
import com.drivers.modules.auth.entity.DriverAuth;
import com.drivers.modules.auth.repository.DriverAuthRepository;
import com.drivers.modules.auth.service.impl.DriverAuthServiceImpl;
import com.drivers.modules.drivers.entity.Driver;
import com.drivers.modules.drivers.entity.DriverStatus;
import com.drivers.modules.drivers.repository.DriverRepository;
import com.drivers.shared.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverAuthServiceTest {

    @Mock
    private DriverAuthRepository driverAuthRepo;
    @Mock
    private AuthenticationManager authManager;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private DriverRepository driverRepository;

    @InjectMocks
    private DriverAuthServiceImpl driverAuthService;

    private UUID driverId;
    private String phone;
    private DriverAuth driverAuth;
    private Driver driver;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        phone = "++996707123456";

        driverAuth = DriverAuth.builder()
                .phone(phone)
                .driverId(driverId)
                .password("password")
                .build();
        driverAuth.id = driverId;

        driver = Driver.builder()
                .fullName("Айдоочу Айдоочубеков")
                .carNumber("01 730 AZX")
                .phone(phone)
                .warehouseId(UUID.randomUUID())
                .status(DriverStatus.ACTIVE)
                .build();
        driver.id = driverId;

        authentication = mock(Authentication.class);
    }

    @Test
    void login_Success_ShouldReturnLoginResponse() {
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(driverAuthRepo.findByPhone(any(String.class))).thenReturn(Optional.of(driverAuth));
        when(driverRepository.findById(any(UUID.class))).thenReturn(Optional.of(driver));
        when(jwtUtil.generateToken(any(Authentication.class), any(UUID.class))).thenReturn("access_token");
        when(jwtUtil.generateRefreshToken(any(Authentication.class))).thenReturn("refresh_token");

        LoginRequest loginRequest = new LoginRequest(phone, "password");
        LoginResponse loginResponse = driverAuthService.login(loginRequest);

        assertNotNull(loginResponse);
        assertEquals("access_token", loginResponse.accessToken());
        assertEquals("refresh_token", loginResponse.refreshToken());
        assertEquals(driver.id, loginResponse.user().id());
        assertEquals(driver.getFullName(), loginResponse.user().fullName());
        assertEquals(driver.getCarNumber(), loginResponse.user().carNumber());
        assertEquals(driver.getStatus(), loginResponse.user().status());

        verify(authManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(driverAuthRepo, times(1)).findByPhone(phone);
        verify(driverRepository, times(1)).findById(driverId);
    }
}