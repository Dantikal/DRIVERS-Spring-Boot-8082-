package com.drivers.modules.returns.controller;

import com.drivers.modules.returns.dto.PhotoUploadResponse;
import com.drivers.modules.returns.dto.ReturnRequestDto;
import com.drivers.modules.returns.dto.req.ReturnCreateReq;
import com.drivers.modules.returns.entity.ReturnStatus;
import com.drivers.modules.returns.service.ReturnService;
import com.drivers.shared.dto.IdempotentResponse;
import com.drivers.shared.util.CurrentDriverId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/drivers/returns/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "Drivers - Returns", description = "API возвратов для мобильного приложения водителя")
public class ReturnSelfController {

    private final ReturnService returnService;

    @GetMapping
    @Operation(summary = "Получить историю своих возвратов")
    public Page<ReturnRequestDto> getMyReturns(@PageableDefault(size = 20) Pageable pageable,
                                               @CurrentDriverId UUID driverId,
                                               @RequestParam(required = false) ReturnStatus status) {
        return returnService.getReturns(pageable, driverId, status);
    }

    @PostMapping
    @Operation(summary = "Создать заявку на возврат")
    public ResponseEntity<ReturnRequestDto> createReturn(@Valid @RequestBody ReturnCreateReq req,
                                                         @CurrentDriverId UUID driverId,
                                                         @RequestHeader(value = "Idempotency-Key") String idempotencyKey) {

        IdempotentResponse<ReturnRequestDto> response = returnService.createReturn(req, driverId, idempotencyKey);

        if (response.isReplayed()) {
            return ResponseEntity.status(HttpStatus.OK)
                    .header("Idempotency-Replayed", "true")
                    .body(response.data());
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response.data());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить детали своего возврата по ID")
    public ReturnRequestDto getReturn(@PathVariable UUID id, @CurrentDriverId UUID driverId) {
        return returnService.getReturn(id, driverId);
    }

    @PostMapping(value = "/upload-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Загрузить фото возврата")
    public PhotoUploadResponse uploadPhoto(@RequestPart("file") MultipartFile file) {
        return returnService.uploadPhoto(file);
    }
}