package com.drivers.modules.returns.service;

import com.drivers.modules.returns.dto.PhotoUploadResponse;
import com.drivers.modules.returns.dto.ReturnRequestDto;
import com.drivers.modules.returns.dto.req.ReturnCreateReq;
import com.drivers.modules.returns.entity.ReturnStatus;
import com.drivers.shared.dto.IdempotentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ReturnService {
    Page<ReturnRequestDto> getReturns(Pageable pageable, UUID driverId, ReturnStatus status);
    IdempotentResponse<ReturnRequestDto> createReturn(ReturnCreateReq req, UUID driverId, String idempotencyKey);

    ReturnRequestDto getReturn(UUID id);
    ReturnRequestDto getReturn(UUID id, UUID driverId);

    PhotoUploadResponse uploadPhoto(MultipartFile file);

    ReturnRequestDto acceptReturn(UUID id);
    ReturnRequestDto rejectReturn(UUID id);
}
