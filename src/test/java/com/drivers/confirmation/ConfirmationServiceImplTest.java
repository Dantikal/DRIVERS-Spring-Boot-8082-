package com.drivers.confirmation;

import com.drivers.modules.events.dto.DriverOrderEvent;
import com.drivers.modules.orders.entity.DriverOrder;
import com.drivers.modules.orders.repository.DriverOrderRepo;
import com.drivers.modules.confirmation.service.impl.ConfirmationServiceImpl;
import com.drivers.shared.exception.ex.OrderNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfirmationServiceImplTest {

    @Mock
    private DriverOrderRepo orderRepo;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private ConfirmationServiceImpl confirmationService;

    @Test
    void confirmationReceipt_Success() {
        UUID dispatchId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        DriverOrder mockOrder = new DriverOrder();
        mockOrder.id = dispatchId;
        mockOrder.setDriverId(driverId);
        mockOrder.setTotalAmount(BigDecimal.TEN);

        when(orderRepo.findById(dispatchId)).thenReturn(Optional.of(mockOrder));

        confirmationService.confirmationReceipt(dispatchId, driverId);

        verify(orderRepo).findById(dispatchId);
        verify(redisTemplate).convertAndSend(eq("orders:updated"), any(DriverOrderEvent.class));
    }

    @Test
    void confirmationReceipt_OrderNotFound_ThrowsException() {
        UUID dispatchId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        when(orderRepo.findById(dispatchId)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> 
            confirmationService.confirmationReceipt(dispatchId, driverId)
        );

        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    @Test
    void confirmationReceipt_WrongDriver_ThrowsAccessDenied() {
        UUID dispatchId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID wrongDriverId = UUID.randomUUID();

        DriverOrder mockOrder = new DriverOrder();
        mockOrder.id = dispatchId;
        mockOrder.setDriverId(wrongDriverId);

        when(orderRepo.findById(dispatchId)).thenReturn(Optional.of(mockOrder));

        assertThrows(AccessDeniedException.class, () -> 
            confirmationService.confirmationReceipt(dispatchId, driverId)
        );

        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }
}
