package com.drivers.modules.returns.service;

import com.drivers.modules.returns.dto.PhotoUploadResponse;
import com.drivers.modules.returns.dto.ReturnRequestDto;
import com.drivers.modules.returns.dto.req.ReturnCreateReq;
import com.drivers.modules.returns.entity.ReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ReturnService {
    Page<ReturnRequestDto> getReturns(Pageable pageable, UUID driverId, ReturnStatus status);
    ReturnRequestDto createReturn(ReturnCreateReq req);
    ReturnRequestDto getReturn(UUID id);
    PhotoUploadResponse uploadPhoto(MultipartFile file);
}
