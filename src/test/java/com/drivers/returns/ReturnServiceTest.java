package com.drivers.returns;

import com.drivers.modules.drivers.service.DriverService;
import com.drivers.modules.returns.dto.PhotoUploadResponse;
import com.drivers.modules.returns.dto.ReturnRequestDto;
import com.drivers.modules.returns.dto.req.ReturnCreateReq;
import com.drivers.modules.returns.dto.req.ReturnItemReq;
import com.drivers.modules.returns.entity.ReturnReason;
import com.drivers.modules.returns.entity.ReturnRequest;
import com.drivers.modules.returns.entity.ReturnStatus;
import com.drivers.modules.returns.repository.ReturnRequestRepo;
import com.drivers.modules.returns.service.impl.ReturnServiceImpl;
import com.drivers.shared.dto.IdempotentResponse;
import com.drivers.shared.exception.ex.ReturnNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import com.drivers.modules.events.publisher.DriverEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReturnServiceTest {

    @Mock
    private ReturnRequestRepo returnRequestRepo;

    @Mock
    private DriverService driverService;

    @Mock
    private DriverEventPublisher eventPublisher;

    @InjectMocks
    private ReturnServiceImpl returnService;

    @TempDir
    Path tempDir;

    private UUID driverId;
    private UUID returnId;
    private ReturnRequest returnRequest;
    private ReturnCreateReq createReq;
    private final String idempotencyKey = "test-key-123";

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        returnId = UUID.randomUUID();

        ReflectionTestUtils.setField(returnService, "returnsUploadDir", tempDir.toString());

        returnRequest = ReturnRequest.builder()
                .driverId(driverId)
                .returnedAt(Instant.now())
                .totalAmount(BigDecimal.valueOf(100.50))
                .status(ReturnStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .items(new ArrayList<>())
                .build();
        returnRequest.id = returnId;

        ReturnItemReq itemReq = new ReturnItemReq(UUID.randomUUID(), 2, 0, ReturnReason.MELTED, null);
        createReq = new ReturnCreateReq(driverId, BigDecimal.valueOf(100.50), List.of(itemReq));
    }

    @Test
    void createReturn_Success_ShouldReturnNew() {
        when(returnRequestRepo.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(returnRequestRepo.saveAndFlush(any(ReturnRequest.class))).thenReturn(returnRequest);

        IdempotentResponse<ReturnRequestDto> res = returnService.createReturn(createReq, driverId, idempotencyKey);

        assertNotNull(res);
        assertFalse(res.isReplayed());
        assertEquals(ReturnStatus.PENDING, res.data().status());
        verify(returnRequestRepo, times(1)).saveAndFlush(any(ReturnRequest.class));
        verify(eventPublisher, times(1)).publishReturnProcessed(any());
    }

    @Test
    void createReturn_WhenIdempotencyHit_ShouldReturnReplayedTrue() {
        when(returnRequestRepo.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(returnRequest));

        IdempotentResponse<ReturnRequestDto> res = returnService.createReturn(createReq, driverId, idempotencyKey);

        assertNotNull(res);
        assertTrue(res.isReplayed());
        assertEquals(returnId, res.data().id());
        verify(returnRequestRepo, never()).saveAndFlush(any());
    }

    @Test
    void createReturn_WhenConcurrencyHit_ShouldCatchExceptionAndReturnExisting() {
        when(returnRequestRepo.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(returnRequest));
        when(returnRequestRepo.saveAndFlush(any(ReturnRequest.class)))
                .thenThrow(new DataIntegrityViolationException("Unique index violation"));

        IdempotentResponse<ReturnRequestDto> res = returnService.createReturn(createReq, driverId, idempotencyKey);

        assertNotNull(res);
        assertTrue(res.isReplayed());
        assertEquals(returnId, res.data().id());
        verify(returnRequestRepo, times(2)).findByIdempotencyKey(idempotencyKey);
    }

    @Test
    void getReturn_Admin_Success() {
        when(returnRequestRepo.findById(returnId)).thenReturn(Optional.of(returnRequest));

        ReturnRequestDto res = returnService.getReturn(returnId);

        assertNotNull(res);
        assertEquals(returnId, res.id());
    }

    @Test
    void getReturn_Admin_NotFound_ShouldThrowException() {
        when(returnRequestRepo.findById(returnId)).thenReturn(Optional.empty());

        assertThrows(ReturnNotFoundException.class, () -> returnService.getReturn(returnId));
    }

    @Test
    void getReturn_Self_Success() {
        when(returnRequestRepo.findById(returnId)).thenReturn(Optional.of(returnRequest));

        ReturnRequestDto res = returnService.getReturn(returnId, driverId);

        assertNotNull(res);
        assertEquals(driverId, res.driverId());
    }

    @Test
    void getReturn_Self_AccessDenied_ShouldThrowException() {
        when(returnRequestRepo.findById(returnId)).thenReturn(Optional.of(returnRequest));
        UUID anotherDriverId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class, () -> returnService.getReturn(returnId, anotherDriverId));
    }

    @Test
    void acceptReturn_Success_ShouldDecreaseDebtAndPublishEvent() {
        when(returnRequestRepo.findById(returnId)).thenReturn(Optional.of(returnRequest));
        when(returnRequestRepo.save(any(ReturnRequest.class))).thenReturn(returnRequest);

        ReturnRequestDto res = returnService.acceptReturn(returnId);

        assertEquals(ReturnStatus.ACCEPTED, res.status());
        verify(driverService, times(1)).decreaseDebt(driverId, BigDecimal.valueOf(100.50));
        verify(eventPublisher, times(1)).publishReturnProcessed(any());
    }

    @Test
    void acceptReturn_WhenNotPending_ShouldThrowException() {
        returnRequest.setStatus(ReturnStatus.ACCEPTED);
        when(returnRequestRepo.findById(returnId)).thenReturn(Optional.of(returnRequest));

        assertThrows(IllegalStateException.class, () -> returnService.acceptReturn(returnId));
        verify(driverService, never()).decreaseDebt(any(), any());
    }

    @Test
    void rejectReturn_Success_ShouldNotDecreaseDebt() {
        when(returnRequestRepo.findById(returnId)).thenReturn(Optional.of(returnRequest));
        when(returnRequestRepo.save(any(ReturnRequest.class))).thenReturn(returnRequest);

        ReturnRequestDto res = returnService.rejectReturn(returnId);

        assertEquals(ReturnStatus.REJECTED, res.status());
        verify(driverService, never()).decreaseDebt(any(), any());
        verify(eventPublisher, times(1)).publishReturnProcessed(any());
    }

    @Test
    void uploadPhoto_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "image content".getBytes());

        PhotoUploadResponse res = returnService.uploadPhoto(file);

        assertNotNull(res);
        assertTrue(res.photoUrl().startsWith("/uploads/returns/"));
        assertTrue(res.photoUrl().endsWith(".jpg"));
    }

    @Test
    void uploadPhoto_NullFile_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> returnService.uploadPhoto(null));
    }

    @Test
    void getReturns_ShouldReturnPage() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<ReturnRequest> page = new PageImpl<>(List.of(returnRequest));
        when(returnRequestRepo.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<ReturnRequestDto> res = returnService.getReturns(pageable, driverId, ReturnStatus.PENDING);

        assertNotNull(res);
        assertEquals(1, res.getContent().size());
    }
}