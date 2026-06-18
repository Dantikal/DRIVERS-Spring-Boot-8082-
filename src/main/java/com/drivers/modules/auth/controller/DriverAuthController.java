package com.drivers.modules.auth.controller;

import com.drivers.modules.auth.dto.req.LoginRequest;
import com.drivers.modules.auth.dto.res.LoginResponse;
import com.drivers.modules.auth.dto.res.LogoutResponse;
import com.drivers.modules.auth.dto.req.RefreshTokenRequest;
import com.drivers.modules.auth.dto.res.RefreshTokenResponse;
import com.drivers.modules.auth.service.DriverAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/drivers/auth")
@RequiredArgsConstructor
public class DriverAuthController {
        private final DriverAuthService driverAuthService;

        @PostMapping("/login")
        public LoginResponse login(@Valid @RequestBody LoginRequest loginRequest) {
            return driverAuthService.login(loginRequest);
        }

        @PostMapping("/refresh")
        public RefreshTokenResponse refreshToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
            return driverAuthService.refreshToken(refreshTokenRequest);
        }

        @PostMapping("/logout")
        public LogoutResponse logout(@Valid @RequestBody RefreshTokenRequest req) {
            return driverAuthService.logout(req);
        }
}
