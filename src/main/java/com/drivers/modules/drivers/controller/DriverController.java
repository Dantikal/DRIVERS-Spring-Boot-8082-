package com.drivers.modules.drivers.controller;

import com.drivers.modules.drivers.dto.DriverDebtDto;
import com.drivers.modules.drivers.dto.DriverDto;
import com.drivers.modules.drivers.dto.req.DriverUpdateReq;
import com.drivers.modules.drivers.service.DriverService;
import com.drivers.shared.util.CurrentDriverId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@PreAuthorize( "hasRole('DRIVER')")
@Tag(name = "Driver", description = "Эндпоинты для самого водителя")
public class DriverController {

    private final DriverService driverService;

    @PutMapping("/me")
    @Operation(summary = "Обновить свои данные")
    public DriverDto updateDriver(
            @CurrentDriverId UUID driverId,
            @Valid @RequestBody DriverUpdateReq dto) {
        return driverService.updateDriver(driverId, dto);
    }

    @GetMapping("/me")
    @Operation(summary = "Получить свой профиль")
    public DriverDto getDriver(@CurrentDriverId UUID driverId) {
        return driverService.getDriver(driverId);
    }

    @GetMapping("/me/debt")
    @Operation(summary = "Получить свой долг")
    public DriverDebtDto getDriverDebt(@CurrentDriverId UUID driverId) {
        return driverService.getDriverDebt(driverId);
    }
}