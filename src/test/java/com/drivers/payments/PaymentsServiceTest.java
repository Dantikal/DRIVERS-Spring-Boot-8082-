package com.drivers.payments;

import com.drivers.modules.drivers.service.DriverService;
import com.drivers.modules.payments.dto.PaymentDto;
import com.drivers.modules.payments.dto.event.PaymentEvent;
import com.drivers.modules.payments.dto.req.PaymentCreateReq;
import com.drivers.modules.payments.entity.DriverPayment;
import com.drivers.modules.payments.entity.PaymentMethod;
import com.drivers.modules.payments.repository.DriverPaymentRepo;
import com.drivers.modules.payments.service.impl.PaymentServiceImpl;
import com.drivers.shared.dto.IdempotentResponse;
import com.drivers.shared.exception.ex.PaymentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import com.drivers.modules.events.publisher.DriverEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentsServiceTest {

    @Mock
    private DriverPaymentRepo paymentRepo;

    @Mock
    private DriverService driverService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Mock
    private DriverEventPublisher eventPublisher;

    private UUID driverId;
    private UUID paymentId;
    private DriverPayment payment;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        payment = DriverPayment.builder()
                .driverId(driverId)
                .amount(BigDecimal.valueOf(1000))
                .paymentMethod(PaymentMethod.CASH)
                .paidAt(Instant.now())
                .build();
        payment.id = paymentId;
    }

    @Test
    void createPayment_AdminRequest_ShouldDecreaseDebtAndSavePaymentAndPublishEvent() {
        // Arrange
        PaymentCreateReq req = new PaymentCreateReq(
                driverId, BigDecimal.valueOf(1000), PaymentMethod.CASH, "Test", UUID.randomUUID()
        );
        String idempotencyKey = UUID.randomUUID().toString();
        when(paymentRepo.saveAndFlush(any(DriverPayment.class))).thenReturn(payment);

        // Act
        IdempotentResponse<PaymentDto> result = paymentService.createPayment(req, idempotencyKey);

        // Assert
        assertNotNull(result);
        assertEquals(paymentId, result.data().id());
        assertEquals(BigDecimal.valueOf(1000), result.data().amount());

        // Проверяем бизнес-логику
        verify(driverService, times(1)).decreaseDebt(driverId, BigDecimal.valueOf(1000));
        verify(paymentRepo, times(1)).saveAndFlush(any(DriverPayment.class));

        // ДОБАВЛЕНО: Проверяем, что событие улетело в Redis!
        verify(eventPublisher, times(1)).publishPaymentReceived(any());
    }

    @Test
    void getPayment_WhenAdmin_ShouldFindByIdOnly() {
        // Arrange
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment));

        // Act
        PaymentDto result = paymentService.getPayment(paymentId, null);

        // Assert
        assertNotNull(result);
        verify(paymentRepo, times(1)).findById(paymentId);
        verify(paymentRepo, never()).findByIdAndDriverId(any(), any());
    }

    @Test
    void getPayment_WhenDriver_ShouldFindByIdAndDriverId() {
        // Arrange
        when(paymentRepo.findByIdAndDriverId(paymentId, driverId)).thenReturn(Optional.of(payment));

        // Act
        PaymentDto result = paymentService.getPayment(paymentId, driverId);

        // Assert
        assertNotNull(result);
        verify(paymentRepo, never()).findById(any());
        verify(paymentRepo, times(1)).findByIdAndDriverId(paymentId, driverId);
    }

    @Test
    void getPayment_WhenNotFound_ShouldThrowException() {
        // Arrange
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(PaymentNotFoundException.class, () -> paymentService.getPayment(paymentId, null));
    }

    @Test
    void getPayments_ShouldReturnPagedResult() {
        // Arrange
        PageRequest pageable = PageRequest.of(0, 20);
        Page<DriverPayment> page = new PageImpl<>(List.of(payment));
        when(paymentRepo.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        // Act
        Page<PaymentDto> result = paymentService.getPayments(pageable, driverId, PaymentMethod.CASH);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals(driverId, result.getContent().get(0).driverId());
    }
    @Test
    void createPayment_WhenNewKey_ShouldDecreaseDebtAndSaveAndPublishEvent() {
        PaymentCreateReq req = new PaymentCreateReq(
                driverId, BigDecimal.valueOf(1000), PaymentMethod.CASH, "Test", UUID.randomUUID()
        );
        String idempotencyKey = UUID.randomUUID().toString();

        when(paymentRepo.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentRepo.saveAndFlush(any(DriverPayment.class))).thenReturn(payment);

        IdempotentResponse<PaymentDto> result = paymentService.createPayment(req, idempotencyKey);

        assertNotNull(result);
        assertEquals(paymentId, result.data().id());

        verify(driverService, times(1)).decreaseDebt(driverId, BigDecimal.valueOf(1000));
        verify(paymentRepo, times(1)).saveAndFlush(any(DriverPayment.class));
        verify(eventPublisher, times(1)).publishPaymentReceived(any());
    }

    @Test
    void createPayment_WhenExistingKey_ShouldReturnExistingAndNotProcessAgain() {
        PaymentCreateReq req = new PaymentCreateReq(
                driverId, BigDecimal.valueOf(1000), PaymentMethod.CASH, "Test", UUID.randomUUID()
        );
        String idempotencyKey = UUID.randomUUID().toString();

        when(paymentRepo.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(payment));

        IdempotentResponse<PaymentDto> result = paymentService.createPayment(req, idempotencyKey);

        assertNotNull(result);
        assertEquals(paymentId, result.data().id());

        verify(driverService, never()).decreaseDebt(any(), any());
        verify(paymentRepo, never()).save(any());
        verify(eventPublisher, never()).publishPaymentReceived(any());
    }

}