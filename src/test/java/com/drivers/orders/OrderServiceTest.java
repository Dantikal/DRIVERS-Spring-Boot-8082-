package com.drivers.orders;

import com.drivers.modules.drivers.service.DriverService;
import com.drivers.modules.events.dto.DriverOrderEvent;
import com.drivers.modules.orders.dto.OrderDto;
import com.drivers.modules.orders.dto.req.OrderCreateReq;
import com.drivers.modules.orders.dto.req.OrderItemReq;
import com.drivers.modules.orders.dto.req.OrderModifyReq;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;

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

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

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
        String idempotencyKey = UUID.randomUUID().toString();

        when(orderRepo.saveAndFlush(any(DriverOrder.class))).thenReturn(driverOrder);

        // Act
        OrderDto res = orderService.createOrder(req, driverId, idempotencyKey);

        // Assert
        assertNotNull(res);
        assertEquals(orderId, res.id());
        assertEquals(OrderStatus.NEW, res.status());
        assertEquals(driverId, res.driverId());

        verify(driverService, times(1)).getDriver(driverId);
        verify(orderRepo, times(1)).saveAndFlush(any(DriverOrder.class));
        verify(redisTemplate, times(1)).convertAndSend(eq("orders:new"), any());
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

        verify(driverService, times(1)).increaseDebt(driverId, BigDecimal.valueOf(1500));
        verify(orderRepo, times(1)).save(driverOrder);
        verify(redisTemplate, times(1)).convertAndSend(eq("orders:updated"), any());
    }

    @Test
    void confirmOrder_WhenStatusNotValid_ShouldThrowIllegalStateException() {
        // Arrange
        driverOrder.setStatus(OrderStatus.CONFIRMED);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(driverOrder));

        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> orderService.confirmOrder(orderId)
        );

        assertTrue(exception.getMessage().contains("Нельзя выдать товар по заказу в статусе"));
        verifyNoInteractions(driverService);
        verify(orderRepo, never()).save(any(DriverOrder.class));
    }

    @Test
    void modifyOrder_Success_ShouldUpdateItemsAndStatus() {
        // Arrange
        UUID newProductId = UUID.randomUUID();
        OrderItemReq updatedItem = new OrderItemReq(newProductId, 10, 10);
        OrderModifyReq modifyReq = new OrderModifyReq(BigDecimal.valueOf(3000), "Updated comment", List.of(updatedItem));

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(driverOrder));
        when(orderRepo.save(any(DriverOrder.class))).thenReturn(driverOrder);

        // Act
        OrderDto res = orderService.modifyOrder(orderId, modifyReq);

        // Assert
        assertNotNull(res);
        assertEquals(OrderStatus.MODIFIED, driverOrder.getStatus());
        assertEquals("Updated comment", driverOrder.getComment());
        assertEquals(BigDecimal.valueOf(3000), driverOrder.getTotalAmount());
        assertEquals(1, driverOrder.getItems().size());
        assertEquals(newProductId, driverOrder.getItems().get(0).getProductId());

        verify(orderRepo, times(1)).save(driverOrder);
        verify(redisTemplate, times(1)).convertAndSend(eq("orders:updated"), any());
    }

    @Test
    void modifyOrder_WhenStatusIsInvalid_ShouldThrowIllegalStateException() {
        // Arrange
        driverOrder.setStatus(OrderStatus.REJECTED);
        OrderModifyReq modifyReq = new OrderModifyReq(BigDecimal.valueOf(2000), "Comment", List.of());
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(driverOrder));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> orderService.modifyOrder(orderId, modifyReq));
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
        verify(redisTemplate, times(1)).convertAndSend(eq("orders:updated"), any());
    }

    @Test
    void rejectOrder_WhenAlreadyDispatched_ShouldThrowIllegalStateException() {
        // Arrange
        driverOrder.setStatus(OrderStatus.DISPATCHED);
        OrderRejectReq rejectReq = new OrderRejectReq("Отмена");
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(driverOrder));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> orderService.rejectOrder(orderId, rejectReq));
        verify(orderRepo, never()).save(any(DriverOrder.class));
    }

    @Test
    void getOrder_ByDriverId_Success_ShouldReturnOrderDto() {
        // Arrange
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(driverOrder));

        // Act
        OrderDto res = orderService.getOrder(orderId, driverId);

        // Assert
        assertNotNull(res);
        assertEquals(orderId, res.id());
    }

    @Test
    void getOrder_ByDriverId_WhenAccessDenied_ShouldThrowAccessDeniedException() {
        // Arrange
        UUID intruderDriverId = UUID.randomUUID();
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(driverOrder));

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> orderService.getOrder(orderId, intruderDriverId));
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

        // Act
        Page<OrderDto> result = orderService.getOrders(pageable, null, null);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(orderRepo, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void getOrders_ShouldFilterByStatus_WhenStatusProvided() {
        // Arrange
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

    @Test
    void confirmOrder_ShouldPublishCorrectRedisEvent() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(driverOrder));
        when(orderRepo.save(any(DriverOrder.class))).thenReturn(driverOrder);

        // Создаем каптор для перехвата топика и самого объекта события
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<DriverOrderEvent> eventCaptor = ArgumentCaptor.forClass(DriverOrderEvent.class);

        orderService.confirmOrder(orderId);

        verify(redisTemplate, times(1)).convertAndSend(topicCaptor.capture(), eventCaptor.capture());

        assertEquals("orders:updated", topicCaptor.getValue());

        DriverOrderEvent publishedEvent = eventCaptor.getValue();
        assertNotNull(publishedEvent);
        assertEquals(orderId, publishedEvent.orderId());
        assertEquals(driverId, publishedEvent.driverId());
        assertEquals(warehouseId, publishedEvent.warehouseId());
        assertEquals(OrderStatus.CONFIRMED, publishedEvent.status());
        assertEquals(BigDecimal.valueOf(1500), publishedEvent.totalAmount());
        assertEquals("ORDER_UPDATED", publishedEvent.eventType());
        assertNotNull(publishedEvent.timestamp());
    }

    @Test
    void createOrder_WhenDriverNotFound_ShouldThrowException() {
        // Arrange
        OrderCreateReq req = new OrderCreateReq(warehouseId, BigDecimal.valueOf(1500), "Comment", List.of());
        // Имитируем, что модуль водителей выбросил исключение (например, EntityNotFoundException)
        doThrow(new RuntimeException("Driver not found")).when(driverService).getDriver(driverId);
        String idempotencyKey = UUID.randomUUID().toString();

        // Act & Assert
        assertThrows(RuntimeException.class, () -> orderService.createOrder(req, driverId, idempotencyKey));
        verify(orderRepo, never()).save(any(DriverOrder.class));
    }

    @Test
    void rejectOrder_WhenAlreadyConfirmed_ShouldThrowIllegalStateException() {
        // Arrange
        driverOrder.setStatus(OrderStatus.CONFIRMED); // Заказ уже подтвержден завскладом
        OrderRejectReq rejectReq = new OrderRejectReq("Отмена");
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(driverOrder));

        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> orderService.rejectOrder(orderId, rejectReq)
        );

        assertTrue(exception.getMessage().contains("Нельзя отклонить заявку в статусе: " + driverOrder.getStatus()));

        verify(orderRepo, never()).save(any(DriverOrder.class));
    }

    @Test
    void modifyOrder_WhenCommentIsNull_ShouldNotFail() {
        // Arrange
        // Проверяем null-safety: коммент null, список позиций пустой
        OrderModifyReq modifyReq = new OrderModifyReq(BigDecimal.valueOf(1000), null, List.of());
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(driverOrder));
        when(orderRepo.save(any(DriverOrder.class))).thenReturn(driverOrder);

        // Act & Assert
        assertDoesNotThrow(() -> orderService.modifyOrder(orderId, modifyReq));
        assertNull(driverOrder.getComment());
        verify(orderRepo, times(1)).save(driverOrder);
    }

    @Test
    void markDispatched_Success_ShouldChangeStatusToDispatched() {
        driverOrder.setStatus(OrderStatus.CONFIRMED);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(driverOrder));
        when(orderRepo.save(any(DriverOrder.class))).thenReturn(driverOrder);

        OrderDto res = orderService.markDispatched(orderId);

        assertNotNull(res);
        assertEquals(OrderStatus.DISPATCHED, res.status());
        verify(orderRepo, times(1)).save(driverOrder);
        verify(redisTemplate, times(1)).convertAndSend(eq("orders:updated"), any());
    }

    @Test
    void markDispatched_WhenStatusNotConfirmed_ShouldThrowIllegalStateException() {
        driverOrder.setStatus(OrderStatus.NEW);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(driverOrder));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> orderService.markDispatched(orderId)
        );
        assertTrue(exception.getMessage().contains("В доставку можно передать только подтвержденную заявку"));
        verify(orderRepo, never()).save(any());
    }


    @Test
    void createOrder_WhenIdempotencyKeyExists_ShouldReturnExistingOrder() {
        String idempotencyKey = "test-key-123";
        OrderCreateReq req = new OrderCreateReq(warehouseId, BigDecimal.valueOf(1500), "Test", List.of());

        when(orderRepo.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(driverOrder));

        OrderDto res = orderService.createOrder(req, driverId, idempotencyKey);

        assertNotNull(res);
        verify(orderRepo, never()).saveAndFlush(any());
    }

    @Test
    void createOrder_WhenConcurrencyRaceCondition_ShouldCatchExceptionAndReturnExistingOrder() {
        String idempotencyKey = "test-key-concurrent";
        OrderCreateReq req = new OrderCreateReq(warehouseId, BigDecimal.valueOf(1500), "Test", List.of());

        when(orderRepo.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(driverOrder));

        when(orderRepo.saveAndFlush(any(DriverOrder.class)))
                .thenThrow(new DataIntegrityViolationException("Unique index violation"));

        OrderDto res = orderService.createOrder(req, driverId, idempotencyKey);

        assertNotNull(res);
        assertEquals(driverOrder.getId(), res.id());
        verify(orderRepo, times(1)).saveAndFlush(any(DriverOrder.class));
        verify(orderRepo, times(2)).findByIdempotencyKey(idempotencyKey);
    }
}