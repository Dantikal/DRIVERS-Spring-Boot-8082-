package com.drivers.modules.auth.service;

import com.drivers.modules.auth.dto.req.LoginRequest;
import com.drivers.modules.auth.dto.res.LoginResponse;
import com.drivers.modules.auth.dto.res.LogoutResponse;
import com.drivers.modules.auth.dto.req.RefreshTokenRequest;
import com.drivers.modules.auth.dto.res.RefreshTokenResponse;

public interface DriverAuthService {
    LoginResponse login(LoginRequest loginRequest);
    RefreshTokenResponse refreshToken(RefreshTokenRequest req);
    LogoutResponse logout(RefreshTokenRequest refreshToken);
}
