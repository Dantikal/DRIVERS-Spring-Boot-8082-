package com.drivers.modules.auth.controller;

import com.drivers.modules.auth.dto.LoginRequest;
import com.drivers.modules.auth.dto.LoginResponse;
import com.drivers.modules.auth.dto.RefreshTokenRequest;
import com.drivers.modules.auth.service.DriverAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/drivers/auth")
@RequiredArgsConstructor
public class DriverAuthController {
        private final DriverAuthService driverAuthService;

        @PostMapping("/login")
        public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
            return ResponseEntity.ok(driverAuthService.login(loginRequest));
        }

        @PostMapping("/refresh")
        public ResponseEntity<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
            return ResponseEntity.ok(driverAuthService.refreshToken(refreshTokenRequest.refreshToken()));
        }

        @PostMapping("/logout")
        public ResponseEntity<Void> logout(@RequestHeader("Authorization") String refreshToken) {
            driverAuthService.logout(refreshToken);
            return ResponseEntity.noContent().build();
        }
}
