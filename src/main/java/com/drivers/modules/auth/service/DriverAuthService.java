package com.drivers.modules.auth.service;

import com.drivers.modules.auth.dto.LoginRequest;
import com.drivers.modules.auth.dto.LoginResponse;

public interface DriverAuthService {
    LoginResponse login(LoginRequest loginRequest);
    LoginResponse refreshToken(String refreshToken);
    void logout(String refreshToken);
}
