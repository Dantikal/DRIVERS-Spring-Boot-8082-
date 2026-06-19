package com.drivers.modules.returns.controller;

import com.drivers.modules.returns.dto.PhotoUploadResponse;
import com.drivers.modules.returns.dto.ReturnRequestDto;
import com.drivers.modules.returns.dto.req.ReturnCreateReq;
import com.drivers.modules.returns.entity.ReturnStatus;
import com.drivers.modules.returns.service.ReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping({"/returns", "/api/returns", "/api/drivers/returns"})
@RequiredArgsConstructor
@Tag(name = "Returns", description = "Возвраты товара")
public class ReturnController {

    private final ReturnService returnService;

    @GetMapping
    @Operation(summary = "Получить список возвратов")
    public Page<ReturnRequestDto> getReturns(@PageableDefault(size = 20) Pageable pageable,
                                             @RequestParam(required = false) UUID driverId,
                                             @RequestParam(required = false) ReturnStatus status) {
        return returnService.getReturns(pageable, driverId, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать возврат")
    public ReturnRequestDto createReturn(@Valid @RequestBody ReturnCreateReq req) {
        return returnService.createReturn(req);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить возврат по ID")
    public ReturnRequestDto getReturn(@PathVariable UUID id) {
        return returnService.getReturn(id);
    }

    @PostMapping(value = "/upload-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Загрузить фото возврата")
    public PhotoUploadResponse uploadPhoto(@RequestPart("file") MultipartFile file) {
        return returnService.uploadPhoto(file);
    }
}
