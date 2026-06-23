package com.drivers.modules.returns.controller;

import com.drivers.modules.returns.dto.ReturnRequestDto;
import com.drivers.modules.returns.entity.ReturnStatus;
import com.drivers.modules.returns.service.ReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/drivers/returns")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
@Tag(name = "Warehouse - Returns", description = "API управления возвратами для склада")
public class ReturnAdminController {

    private final ReturnService returnService;

    @GetMapping
    @Operation(summary = "Получить список всех возвратов (с фильтрами)")
    public Page<ReturnRequestDto> getAllReturns(@PageableDefault(size = 20) Pageable pageable,
                                                @RequestParam(required = false) UUID driverId,
                                                @RequestParam(required = false) ReturnStatus status) {
        return returnService.getReturns(pageable, driverId, status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить детали возврата по ID")
    public ReturnRequestDto getReturnDetails(@PathVariable UUID id) {
        return returnService.getReturn(id);
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Подтвердить возврат и списать долг")
    public ReturnRequestDto acceptReturn(@PathVariable UUID id) {
        return returnService.acceptReturn(id);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Отклонить возврат (без изменения долга)")
    public ReturnRequestDto rejectReturn(@PathVariable UUID id) {
        return returnService.rejectReturn(id);
    }
}