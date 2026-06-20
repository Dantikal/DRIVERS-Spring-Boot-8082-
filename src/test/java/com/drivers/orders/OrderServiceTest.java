package com.drivers.orders;

import com.drivers.modules.drivers.service.DriverService;
import com.drivers.modules.orders.dto.OrderDto;
import com.drivers.modules.orders.dto.req.OrderCreateReq;
import com.drivers.modules.orders.dto.req.OrderItemReq;
import com.drivers.modules.orders.dto.req.OrderRejectReq;
import com.drivers.modules.orders.entity.DriverOrder;
import com.drivers.modules.orders.entity.DriverOrderItem;
import com.drivers.modules.orders.entity.OrderStatus;
import com.drivers.modules.orders.repository.DriverOrderRepo;
import com.drivers.modules.orders.service.impl.OrderServiceImpl;
import com.drivers.shared.exception.ex.OrderNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private DriverOrderRepo orderRepo;

    @Mock
    private DriverService driverService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UUID driverId;
    private UUID warehouseId;
    private UUID orderId;
    private DriverOrder driverOrder;
    private DriverOrderItem orderItem;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        driverOrder = DriverOrder.builder()
                .driverId(driverId)
                .warehouseId(warehouseId)
                .status(OrderStatus.NEW)
                .requestedAt(LocalDateTime.now())
                .totalAmount(BigDecimal.valueOf(1500))
                .comment("Test comment")
                .items(new ArrayList<>())
                .build();
        driverOrder.id = orderId;

        orderItem = DriverOrderItem.builder()
                .order(driverOrder)
                .productId(UUID.randomUUID())
                .requestedQty(5)
                .approvedQty(null)
                .build();
        orderItem.id = UUID.randomUUID();

        driverOrder.getItems().add(orderItem);
    }

    @Test
    void createOrder_Success_ShouldReturnOrderDto() {
        // Arrange
        OrderItemReq itemReq = new OrderItemReq(orderItem.getProductId(), 5, null);
        OrderCreateReq req = new OrderCreateReq(warehouseId, BigDecimal.valueOf(1500), "Test comment", List.of(itemReq));

        when(orderRepo.save(any(DriverOrder.class))).thenReturn(driverOrder);

        // Act
        OrderDto res = orderService.createOrder(req, driverId);

        // Assert
        assertNotNull(res);
        assertEquals(orderId, res.id());
        assertEquals(OrderStatus.NEW, res.status());
        assertEquals(driverId, res.driverId());

        verify(driverService, times(1)).getDriver(driverId);
        verify(orderRepo, times(1)).save(any(DriverOrder.class));
    }

    @Test
    void confirmOrder_Success_ShouldIncreaseDriverDebtAndChangeStatus() {
        // Arrange
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(driverOrder));
        when(orderRepo.save(any(DriverOrder.class))).thenReturn(driverOrder);

        // Act
        OrderDto res = orderService.confirmOrder(orderId);

        // Assert
        assertNotNull(res);
        assertEquals(OrderStatus.CONFIRMED, res.status());
        assertEquals(orderItem.getRequestedQty(), driverOrder.getItems().get(0).getApprovedQty());

        // Железная проверка интеграции с модулем водителей
        verify(driverService, times(1)).increaseDebt(driverId, BigDecimal.valueOf(1500));
        verify(orderRepo, times(1)).save(driverOrder);
    }

    @Test
    void confirmOrder_WhenStatusNotValid_ShouldThrowIllegalStateException() {
        // Arrange
        driverOrder.setStatus(OrderStatus.CONFIRMED); // Заказ уже подтвержден
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(driverOrder));

        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> orderService.confirmOrder(orderId)
        );

        assertTrue(exception.getMessage().contains("Нельзя выдать товар по заказу в статусе"));
        verifyNoInteractions(driverService); // Долг не должен начисляться повторно!
        verify(orderRepo, never()).save(any(DriverOrder.class));
    }

    @Test
    void rejectOrder_Success_ShouldChangeStatusToRejected() {
        // Arrange
        OrderRejectReq rejectReq = new OrderRejectReq("Товара нет на остатках");
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(driverOrder));
        when(orderRepo.save(any(DriverOrder.class))).thenReturn(driverOrder);

        // Act
        OrderDto res = orderService.rejectOrder(orderId, rejectReq);

        // Assert
        assertNotNull(res);
        assertEquals(OrderStatus.REJECTED, res.status());
        assertEquals("Товара нет на остатках", driverOrder.getComment());

        verify(orderRepo, times(1)).save(driverOrder);
    }

    @Test
    void getOrder_WhenNotFound_ShouldThrowOrderNotFoundException() {
        // Arrange
        UUID fakeOrderId = UUID.randomUUID();
        when(orderRepo.findById(fakeOrderId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(OrderNotFoundException.class, () -> orderService.getOrder(fakeOrderId));
    }

    @Test
    void getOrders_ShouldFilterByDriverId_WhenDriverIdProvided() {
        // Arrange
        UUID driverId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Page<DriverOrder> page = new PageImpl<>(List.of(driverOrder));

        when(orderRepo.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        // Act
        Page<OrderDto> result = orderService.getOrders(pageable, driverId, null);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(orderRepo, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void getOrders_ShouldReturnAll_WhenDriverIdIsNull() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<DriverOrder> page = new PageImpl<>(List.of(driverOrder));

        when(orderRepo.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        // Act — null = зав. склад смотрит все заявки
        Page<OrderDto> result = orderService.getOrders(pageable, null, null);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(orderRepo, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void getOrders_ShouldFilterByStatus_WhenStatusProvided() {
        // Arrange
        UUID driverId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Page<DriverOrder> page = new PageImpl<>(List.of(driverOrder));

        when(orderRepo.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        // Act
        Page<OrderDto> result = orderService.getOrders(pageable, driverId, OrderStatus.NEW);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(orderRepo, times(1)).findAll(any(Specification.class), eq(pageable));
    }
}