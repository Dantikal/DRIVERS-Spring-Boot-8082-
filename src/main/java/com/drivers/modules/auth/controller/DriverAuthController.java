package com.drivers.modules.auth.controller;

import com.drivers.modules.auth.dto.req.LoginRequest;
import com.drivers.modules.auth.dto.res.LoginResponse;
import com.drivers.modules.auth.dto.res.LogoutResponse;
import com.drivers.modules.auth.dto.req.RefreshTokenRequest;
import com.drivers.modules.auth.dto.res.RefreshTokenResponse;
import com.drivers.modules.auth.service.DriverAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/drivers/auth")
@RequiredArgsConstructor
@Tag(name="Driver - Auth", description = "Эндпоинты для аутентификации водителей")
public class DriverAuthController {

    private final DriverAuthService driverAuthService;

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Login водителя", description = "Войти по номеру телефона и паролю")
    public LoginResponse login(@Valid @RequestBody LoginRequest loginRequest) {
        return driverAuthService.login(loginRequest);
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "JWT Refresh Token", description = "Получение jwt access токена через refresh")
    public RefreshTokenResponse refreshToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        return driverAuthService.refreshToken(refreshTokenRequest);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Выход из аккаунта", description = "Выход из аккаунта и внесение токена в черный список redis")
    public LogoutResponse logout(@Valid @RequestBody RefreshTokenRequest req) {
        return driverAuthService.logout(req);
    }
}
