package com.drivers.modules.auth.service.impl;

import com.drivers.modules.auth.dto.*;
import com.drivers.modules.auth.dto.req.LoginRequest;
import com.drivers.modules.auth.dto.req.RefreshTokenRequest;
import com.drivers.modules.auth.dto.res.LoginResponse;
import com.drivers.modules.auth.dto.res.LogoutResponse;
import com.drivers.modules.auth.dto.res.RefreshTokenResponse;
import com.drivers.modules.auth.entity.DriverAuth;
import com.drivers.modules.auth.repository.DriverAuthRepository;
import com.drivers.modules.auth.service.DriverAuthService;
import com.drivers.modules.drivers.entity.Driver;
import com.drivers.modules.drivers.repository.DriverRepository;
import com.drivers.shared.exception.DriverNotFoundException;
import com.drivers.shared.exception.InvalidCredentialsException;
import com.drivers.shared.util.CustomUserDetailsService;
import com.drivers.shared.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

record JwtTokens(String accessToken, String refreshToken){}

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverAuthServiceImpl implements DriverAuthService {
    private final DriverAuthRepository driverAuthRepository;
    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;
    private final StringRedisTemplate redisTemplate;
    private final DriverRepository driverRepository;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest loginRequest) {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(loginRequest.phone(), loginRequest.password());
        Authentication auth;
        try {
            auth = authManager.authenticate(authenticationToken);
        } catch (Exception e) {
            throw new InvalidCredentialsException("Номер телефона или пароль неверны");
        }

        DriverAuth driverAuth = driverAuthRepository.findByPhone(loginRequest.phone()).orElseThrow(() -> new DriverNotFoundException(
                "Водитель с номером телефона: " + loginRequest.phone() + " не найден"
        ));
        driverAuth.setLastLogin(Instant.now());

        log.info("Driver authenticated via phone: {}, system assigned ROLE_DRIVER", loginRequest.phone());
        return loginResponse(auth, driverAuth.getDriverId());
    }

    private JwtTokens issueTokens(Authentication auth, UUID driverId) {
        String access = jwtUtil.generateToken(auth, driverId);
        String refresh = jwtUtil.generateRefreshToken(auth);
        return new JwtTokens(access, refresh);
    }

    private LoginResponse loginResponse(Authentication auth, UUID driverId) {
        JwtTokens tokens = issueTokens(auth, driverId);
        Driver driver = driverRepository.findById(driverId).orElseThrow(() -> new DriverNotFoundException(
                "Водитель с ID: " + driverId + " не найден"
        ));

        User userData = User.builder()
                .id(driver.getId())
                .fullName(driver.getFullName())
                .carNumber(driver.getCarNumber())
                .warehouseId(driver.getWarehouseId())
                .phone(driver.getPhone())
                .status(driver.getStatus())
                .build();

        return LoginResponse.builder()
                .accessToken(tokens.accessToken())
                .refreshToken(tokens.refreshToken())
                .user(userData)
                .build();
    }

    @Override
    @Transactional
    public RefreshTokenResponse refreshToken(RefreshTokenRequest req) {
        String phone;
        String refreshToken = req.refreshToken();
        try {
            phone = jwtUtil.extractUserName(req.refreshToken());
        } catch (Exception e) {
            throw new InvalidCredentialsException("Недействительный или истёкший токен обновления");
        }

        DriverAuth driverAuth = driverAuthRepository.findByPhone(phone).orElseThrow(() -> new DriverNotFoundException(
                "Водитель с номером телефона: " + phone + " не найден"
        ));
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(phone);

        if (!jwtUtil.validateToken(refreshToken, userDetails)) {
            throw new InvalidCredentialsException("Недействительный или истёкший токен обновления");
        }

        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());

        String access = jwtUtil.generateToken(authenticationToken, driverAuth.getDriverId());
        return new RefreshTokenResponse(access);
    }

    @Override
    public LogoutResponse logout(RefreshTokenRequest req) {
        String token = req.refreshToken();
        if (token == null || token.isEmpty()) {
            throw new InvalidCredentialsException("Токен обновления обязателен для выхода");
        }
        String cleanedToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        try {
            Date expirationDate = jwtUtil.extractAllClaims(cleanedToken).getExpiration();
            long remainingTime = expirationDate.getTime() - System.currentTimeMillis();

            if (remainingTime > 0) {
                redisTemplate.opsForValue().set(
                        "blacklist:" + cleanedToken,
                        "revoked",
                        remainingTime,
                        TimeUnit.MILLISECONDS);
            }
            log.info("Token successfully added to blacklist. Remaining time = {}ms", remainingTime);
        } catch (Exception e) {
            log.warn("Failed to add token to blacklist: {}", e.getMessage());
        }
        return new LogoutResponse("Выход выполнен");
    }
}