    package com.drivers.modules.drivers.controller;

    import com.drivers.modules.drivers.dto.req.DriverCreateReq;
    import com.drivers.modules.drivers.dto.DriverDebtDto;
    import com.drivers.modules.drivers.dto.DriverDto;
    import com.drivers.modules.drivers.dto.req.DriverUpdateReq;
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
    import org.springframework.web.bind.annotation.*;

    import java.math.BigDecimal;
    import java.util.UUID;

    @RestController
    @RequestMapping("/api/drivers")
    @RequiredArgsConstructor
    @Tag(name = "Driver Management", description = "Управление справочником водителей и их балансами")
    public class DriverController {

        private final DriverService driverService;

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Создать нового водителя", description = "Регистрирует водителя в системе и инициализирует ему нулевой баланс долга")
        public DriverDto createDriver(@Valid @RequestBody DriverCreateReq driverCreateReq) {
            return driverService.createDriver(driverCreateReq);
        }

        @PutMapping("/{id}")
        @Operation(summary = "Обновить данные водителя", description = "Позволяет изменить ФИО, гос. номер машины или статус водителя")
        public DriverDto updateDriver(@PathVariable UUID id, @Valid @RequestBody DriverUpdateReq dto) {
            return driverService.updateDriver(id, dto);
        }

        @GetMapping("/{id}")
        @Operation(summary = "Получить водителя по ID")
        public DriverDto getDriver(@PathVariable UUID id) {
            return driverService.getDriver(id);
        }

        @GetMapping
        @Operation(summary = "Получить список водителей (Пагинация)", description = "Возвращает постраничный список всех водителей в системе")
        public Page<DriverDto> getAllDrivers(@PageableDefault(size = 20) Pageable pageable,
                                             @RequestParam(required = false) DriverStatus status,
                                             @RequestParam(required = false) UUID warehouseId,
                                             @RequestParam(required = false) String search) {
            return driverService.getAllDrivers(pageable, status, warehouseId, search);
        }

        @GetMapping("/{id}/debt")
        @Operation(summary = "Получить текущий долг конкретного водителя", description = "Запрашивает актуальный баланс долга из таблицы driver_debts")
        public DriverDebtDto getDriverDebt(@PathVariable UUID id) {
            return driverService.getDriverDebt(id);
        }

        @GetMapping("/debts")
        @Operation(summary = "Получить список долгов всех водителей (Пагинация)", description = "Используется бухгалтерией или зав. склада для мониторинга дебиторской задолженности")
        public Page<DriverDebtDto> getAllDebts(
                @RequestParam(required = false) UUID warehouseId,
                @RequestParam(required = false) BigDecimal minDebt,
                @PageableDefault(size = 20, direction = Sort.Direction.DESC, sort = "totalDebt") Pageable pageable) {
            return driverService.getAllDebts(pageable, warehouseId, minDebt);
        }
    }