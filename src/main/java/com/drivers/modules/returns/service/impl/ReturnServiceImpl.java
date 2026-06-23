package com.drivers.modules.returns.service.impl;

import com.drivers.modules.drivers.service.DriverService;
import com.drivers.modules.returns.dto.PhotoUploadResponse;
import com.drivers.modules.returns.dto.ReturnItemDto;
import com.drivers.modules.returns.dto.ReturnRequestDto;
import com.drivers.modules.returns.dto.event.ReturnEvent;
import com.drivers.modules.returns.dto.req.ReturnCreateReq;
import com.drivers.modules.returns.dto.req.ReturnItemReq;
import com.drivers.modules.returns.entity.ReturnItem;
import com.drivers.modules.returns.entity.ReturnRequest;
import com.drivers.modules.returns.entity.ReturnStatus;
import com.drivers.modules.returns.repository.ReturnRequestRepo;
import com.drivers.modules.returns.service.ReturnService;
import com.drivers.shared.dto.IdempotentResponse;
import com.drivers.shared.exception.ex.ReturnNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnServiceImpl implements ReturnService {

    private final ReturnRequestRepo returnRequestRepo;
    private final DriverService driverService;

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${drivers.uploads.returns-dir:uploads/returns}")
    private String returnsUploadDir;

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnRequestDto> getReturns(Pageable pageable, UUID driverId, ReturnStatus status) {
        Specification<ReturnRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (driverId != null) {
                predicates.add(cb.equal(root.get("driverId"), driverId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return returnRequestRepo.findAll(spec, pageable).map(this::toDto);
    }

    @Override
    @Transactional
    public IdempotentResponse<ReturnRequestDto> createReturn(ReturnCreateReq req, UUID driverId, String idempotencyKey) {
        Optional<ReturnRequest> optionalReturn = returnRequestRepo.findByIdempotencyKey(idempotencyKey);
        if (optionalReturn.isPresent()) {
            log.info("Idempotency hit: Возврат существующей заявки {}", optionalReturn.get().getId());
            return new IdempotentResponse<>(toDto(optionalReturn.get()), true); // <-- isReplayed = true
        }

        ReturnRequest returnRequest = ReturnRequest.builder()
                .driverId(driverId)
                .returnedAt(Instant.now())
                .totalAmount(req.totalAmount())
                .status(ReturnStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .items(new ArrayList<>())
                .build();

        req.items().forEach(item -> returnRequest.getItems().add(toEntity(item, returnRequest)));

        try {
            ReturnRequest saved = returnRequestRepo.saveAndFlush(returnRequest);
            publishReturnEvent(saved, "RETURN_CREATED");
            log.info("Created return {} for driver {}", saved.getId(), saved.getDriverId());

            return new IdempotentResponse<>(toDto(saved), false);

        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrency hit for idempotency key {}. Fetching saved return request.", idempotencyKey);
            ReturnRequest racedReturn = returnRequestRepo.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new RuntimeException("Неожиданная ошибка параллельного выполнения"));

            return new IdempotentResponse<>(toDto(racedReturn), true); // <-- isReplayed = true
        }
    }
    @Override
    @Transactional(readOnly = true)
    public ReturnRequestDto getReturn(UUID id) {
        return toDto(getReturnById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnRequestDto getReturn(UUID id, UUID driverId) {
        ReturnRequest returnRequest = getReturnById(id);
        if (!returnRequest.getDriverId().equals(driverId)) {
            throw new AccessDeniedException("Вы не имеете доступа к данной заявке на возврат");
        }
        return toDto(returnRequest);
    }

    @Override
    public PhotoUploadResponse uploadPhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл обязателен для загрузки");
        }

        String originalFilename = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "return-photo"));
        String extension = getExtension(originalFilename);
        String filename = UUID.randomUUID() + extension;
        Path uploadDir = Paths.get(returnsUploadDir).toAbsolutePath().normalize();
        Path target = uploadDir.resolve(filename).normalize();

        if (!target.startsWith(uploadDir)) {
            throw new IllegalArgumentException("Некорректное имя файла");
        }

        try {
            Files.createDirectories(uploadDir);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось загрузить файл", ex);
        }

        String photoUrl = "/uploads/returns/" + filename;
        log.info("Uploaded return photo {}", photoUrl);
        return PhotoUploadResponse.builder().photoUrl(photoUrl).build();
    }

    @Override
    @Transactional
    public ReturnRequestDto acceptReturn(UUID id) {
        ReturnRequest returnRequest = getReturnById(id);

        if (returnRequest.getStatus() != ReturnStatus.PENDING) {
            throw new IllegalStateException("Можно подтвердить только возврат в статусе PENDING");
        }

        returnRequest.setStatus(ReturnStatus.ACCEPTED);
        ReturnRequest saved = returnRequestRepo.save(returnRequest);

        driverService.decreaseDebt(saved.getDriverId(), saved.getTotalAmount());

        publishReturnEvent(saved, "RETURN_ACCEPTED");

        log.info("Зав. склада принял возврат {}. Долг водителя {} уменьшен на {}",
                id, saved.getDriverId(), saved.getTotalAmount());

        return toDto(saved);
    }

    @Override
    @Transactional
    public ReturnRequestDto rejectReturn(UUID id) {
        ReturnRequest returnRequest = getReturnById(id);

        if (returnRequest.getStatus() != ReturnStatus.PENDING) {
            throw new IllegalStateException("Можно отклонить только возврат в статусе PENDING");
        }

        returnRequest.setStatus(ReturnStatus.REJECTED);
        ReturnRequest saved = returnRequestRepo.save(returnRequest);

        publishReturnEvent(saved, "RETURN_REJECTED");

        log.info("Зав. склада отклонил возврат {}. Долг водителя {} не изменился", id, saved.getDriverId());

        return toDto(saved);
    }

    private void publishReturnEvent(ReturnRequest returnRequest, String eventType) {
        try {
            List<ReturnEvent.ReturnItemEvent> itemEvents = returnRequest.getItems().stream()
                    .map(item -> ReturnEvent.ReturnItemEvent.builder()
                            .productId(item.getProductId())
                            .qtyBoxes(item.getQtyBoxes())
                            .qtyPieces(item.getQtyPieces())
                            .reason(item.getReason())
                            .build())
                    .toList();

            ReturnEvent event = ReturnEvent.builder()
                    .returnId(returnRequest.getId())
                    .driverId(returnRequest.getDriverId())
                    .status(returnRequest.getStatus())
                    .totalAmount(returnRequest.getTotalAmount())
                    .eventType(eventType)
                    .timestamp(LocalDateTime.now().toString())
                    .items(itemEvents)
                    .build();

            redisTemplate.convertAndSend("returns:processed", event);
            log.info("Опубликовано событие {} для возврата {}", event.eventType(), returnRequest.getId());
        } catch (Exception e) {
            log.error("Не удалось опубликовать событие возврата {}: {}", returnRequest.getId(), e.getMessage());
        }
    }



    private ReturnRequest getReturnById(UUID id) {
        return returnRequestRepo.findById(id)
                .orElseThrow(() -> new ReturnNotFoundException("Возврат с ID: " + id + " не найден"));
    }

    private ReturnItem toEntity(ReturnItemReq req, ReturnRequest returnRequest) {
        return ReturnItem.builder()
                .returnRequest(returnRequest)
                .productId(req.productId())
                .qtyBoxes(req.qtyBoxes())
                .qtyPieces(req.qtyPieces())
                .reason(req.reason())
                .photoUrl(req.photoUrl())
                .build();
    }

    private ReturnRequestDto toDto(ReturnRequest returnRequest) {
        return ReturnRequestDto.builder()
                .id(returnRequest.getId())
                .driverId(returnRequest.getDriverId())
                .returnedAt(returnRequest.getReturnedAt())
                .totalAmount(returnRequest.getTotalAmount())
                .status(returnRequest.getStatus())
                .items(returnRequest.getItems().stream().map(this::toItemDto).toList())
                .createdAt(returnRequest.getCreatedAt())
                .updatedAt(returnRequest.getUpdatedAt())
                .build();
    }

    private ReturnItemDto toItemDto(ReturnItem item) {
        return ReturnItemDto.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .qtyBoxes(item.getQtyBoxes())
                .qtyPieces(item.getQtyPieces())
                .reason(item.getReason())
                .photoUrl(item.getPhotoUrl())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dotIndex).toLowerCase(Locale.ROOT);
        return extension.replaceAll("[^a-z0-9.]", "");
    }
}
