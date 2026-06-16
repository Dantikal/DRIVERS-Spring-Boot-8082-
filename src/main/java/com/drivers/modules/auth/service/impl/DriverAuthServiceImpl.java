package com.drivers.modules.auth.service.impl;

import com.drivers.modules.auth.dto.LoginRequest;
import com.drivers.modules.auth.dto.LoginResponse;
import com.drivers.modules.auth.entity.DriverAuth;
import com.drivers.modules.auth.repository.DriverAuthRepo;
import com.drivers.modules.auth.service.DriverAuthService;
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

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverAuthServiceImpl implements DriverAuthService {
    private final DriverAuthRepo driverAuthRepo;
    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest loginRequest) {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(loginRequest.phone(), loginRequest.password());
        Authentication auth;
        try{
            auth = authManager.authenticate(
                    authenticationToken);
        } catch (Exception e){
            throw new InvalidCredentialsException("Phone number or password is incorrect");
        }

        DriverAuth driverAuth = driverAuthRepo.findByPhone(loginRequest.phone()).orElseThrow(()->new DriverNotFoundException(
                "Driver with phone: " + loginRequest.phone() + " not found"
        ));
        log.info("Driver authenticated via phone: {}, system assigned ROLE_DRIVER", loginRequest.phone());
        return issueTokens(auth, driverAuth.getDriverId());
    }

    private LoginResponse issueTokens(Authentication auth, UUID driverId) {
        String access = jwtUtil.generateToken(auth, driverId);
        String refresh = jwtUtil.generateRefreshToken(auth);

        return new LoginResponse(access,refresh);
    }

    @Override
    @Transactional
    public LoginResponse refreshToken(String refreshToken) {
        String phone;
        try {
            phone = jwtUtil.extractUserName(refreshToken);
        }catch (Exception e){
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }

        DriverAuth driverAuth = driverAuthRepo.findByPhone(phone).orElseThrow(
                ()->new DriverNotFoundException("Driver with phone: " + phone + " not found"));
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(phone);

        if(!jwtUtil.validateToken(refreshToken, userDetails)){
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());

        return issueTokens(authenticationToken, driverAuth.getDriverId());
    }

    @Override
    public void logout(String token) {
        if(token == null || token.isEmpty()){
            return;
        }
        String cleanedToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        try{
            Date expirationDate = jwtUtil.extractAllClaims(cleanedToken).getExpiration();
            long remainingTime = expirationDate.getTime() - System.currentTimeMillis();

            if(remainingTime > 0){
                redisTemplate.opsForValue().set(
                        "blacklist:" + jwtUtil.extractUserName(cleanedToken),
                        "revoked",
                        remainingTime,
                        TimeUnit.MILLISECONDS);
            }
            log.info("Token successfully added to blacklist. Remaining time = {}ms", remainingTime);
        }catch (Exception e){
            log.warn("Failed to add token to blacklist: {}", e.getMessage());

        }
    }
}
