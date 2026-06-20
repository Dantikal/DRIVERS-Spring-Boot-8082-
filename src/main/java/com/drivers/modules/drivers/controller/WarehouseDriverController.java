package com.drivers.modules.drivers.controller;

import com.drivers.modules.drivers.dto.DriverDebtDto;
import com.drivers.modules.drivers.dto.DriverDto;
import com.drivers.modules.drivers.dto.req.DriverCreateReq;
import com.drivers.modules.drivers.entity.DriverStatus;
import com.drivers.modules.drivers.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/warehouse/drivers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
@Tag(name = "Warehouse — Drivers", description = "Управление водителями для завскладом")
public class WarehouseDriverController {

    private final DriverService driverService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Зарегистрировать нового водителя")
    public DriverDto createDriver(@Valid @RequestBody DriverCreateReq req) {
        return driverService.createDriver(req);
    }

    @GetMapping("/{driverId}")
    @Operation(summary = "Получить водителя по ID")
    public DriverDto getDriver(@PathVariable UUID driverId) {
        return driverService.getDriver(driverId);
    }

    @GetMapping
    @Operation(summary = "Список всех водителей с пагинацией")
    public Page<DriverDto> getAllDrivers(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) DriverStatus status,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String search) {
        return driverService.getAllDrivers(pageable, status, warehouseId, search);
    }

    @GetMapping("/debts")
    @Operation(summary = "Долги всех водителей с пагинацией")
    public Page<DriverDebtDto> getAllDebts(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) BigDecimal minDebt,
            @PageableDefault(size = 20, sort = "totalDebt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return driverService.getAllDebts(pageable, warehouseId, minDebt);
    }
}