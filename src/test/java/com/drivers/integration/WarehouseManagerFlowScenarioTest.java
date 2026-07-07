package com.drivers.integration;

import com.drivers.modules.drivers.dto.DriverDto;
import com.drivers.modules.drivers.dto.req.DriverCreateReq;
import com.drivers.modules.drivers.entity.DriverStatus;
import com.drivers.modules.drivers.service.DriverService;
import com.drivers.modules.orders.dto.OrderDto;
import com.drivers.modules.orders.dto.req.OrderModifyReq;
import com.drivers.modules.orders.dto.req.OrderRejectReq;
import com.drivers.modules.orders.entity.OrderStatus;
import com.drivers.modules.orders.service.OrderService;
import com.drivers.modules.payments.dto.PaymentDto;
import com.drivers.modules.payments.dto.req.PaymentCreateReq;
import com.drivers.modules.payments.entity.PaymentMethod;
import com.drivers.modules.payments.service.PaymentService;
import com.drivers.modules.returns.dto.ReturnRequestDto;
import com.drivers.modules.returns.service.ReturnService;
import com.drivers.shared.dto.IdempotentResponse;
import com.drivers.shared.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class WarehouseManagerFlowScenarioTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private DriverService driverService;
    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private ReturnService returnService;

    private HttpHeaders adminHeaders;

    @BeforeEach
    void setUp() {
        UUID managerId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "admin_manager",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER"))
        );
        String adminToken = jwtUtil.generateToken(authentication, managerId, UUID.randomUUID());

        adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("Full Warehouse Manager Flow: Register Driver -> Manage Orders -> Accept Payment -> Approve Return")
    void executeFullWarehouseManagerLifecycle() throws Exception {

        DriverCreateReq driverCreateReq = new DriverCreateReq(
                "Нурлан Жумабеков",
                "B777BB01",
                "+996555999888",
                "securepass",
                UUID.randomUUID()
        );
        when(driverService.createDriver(any())).thenReturn(
                new DriverDto(UUID.randomUUID(), "Нурлан Жумабеков", "B777BB01", "+996555999888", UUID.randomUUID(), DriverStatus.ACTIVE, null, null)
        );

        HttpEntity<String> createDriverReq = new HttpEntity<>(objectMapper.writeValueAsString(driverCreateReq), adminHeaders);
        ResponseEntity<DriverDto> createDriverRes = restTemplate.postForEntity("/api/drivers", createDriverReq, DriverDto.class);
        assertEquals(HttpStatus.CREATED, createDriverRes.getStatusCode());
        assertEquals("Нурлан Жумабеков", createDriverRes.getBody().fullName());

        HttpEntity<Void> getReq = new HttpEntity<>(adminHeaders);
        ResponseEntity<String> driversRes = restTemplate.exchange("/api/drivers", HttpMethod.GET, getReq, String.class);
        assertEquals(HttpStatus.OK, driversRes.getStatusCode());

        ResponseEntity<String> debtsRes = restTemplate.exchange("/api/drivers/debts", HttpMethod.GET, getReq, String.class);
        assertEquals(HttpStatus.OK, debtsRes.getStatusCode());

        UUID orderId = UUID.randomUUID();
        OrderDto orderDto = new OrderDto(orderId, UUID.randomUUID(), UUID.randomUUID(), OrderStatus.CONFIRMED, null, new BigDecimal("5000.00"), null, null, null, null);

        when(orderService.confirmOrder(any())).thenReturn(orderDto);
        when(orderService.markDispatched(any())).thenReturn(new OrderDto(orderId, UUID.randomUUID(), UUID.randomUUID(), OrderStatus.DISPATCHED, null, new BigDecimal("5000.00"), null, null, null, null));
        when(orderService.rejectOrder(any(), any())).thenReturn(new OrderDto(orderId, UUID.randomUUID(), UUID.randomUUID(), OrderStatus.REJECTED, null, new BigDecimal("5000.00"), null, null, null, null));
        when(orderService.modifyOrder(any(), any())).thenReturn(new OrderDto(orderId, UUID.randomUUID(), UUID.randomUUID(), OrderStatus.MODIFIED, null, new BigDecimal("6000.00"), null, null, null, null));

        ResponseEntity<String> ordersListRes = restTemplate.exchange("/api/drivers/orders", HttpMethod.GET, getReq, String.class);
        assertEquals(HttpStatus.OK, ordersListRes.getStatusCode());

        OrderModifyReq modifyReq = new OrderModifyReq(
                new BigDecimal("6000.00"),
                "Жаңыртылды",
                List.of(new com.drivers.modules.orders.dto.req.OrderItemReq(UUID.randomUUID(), 10, null))
        );
        HttpEntity<String> modifyHttpReq = new HttpEntity<>(objectMapper.writeValueAsString(modifyReq), adminHeaders);
        ResponseEntity<OrderDto> modifyRes = restTemplate.postForEntity("/api/drivers/orders/" + orderId + "/modify", modifyHttpReq, OrderDto.class);
        assertEquals(HttpStatus.OK, modifyRes.getStatusCode());

        ResponseEntity<OrderDto> confirmRes = restTemplate.postForEntity("/api/drivers/orders/" + orderId + "/confirm", getReq, OrderDto.class);
        assertEquals(HttpStatus.OK, confirmRes.getStatusCode());
        assertEquals(OrderStatus.CONFIRMED, confirmRes.getBody().status());

        ResponseEntity<OrderDto> dispatchRes = restTemplate.postForEntity("/api/drivers/orders/" + orderId + "/dispatch", getReq, OrderDto.class);
        assertEquals(HttpStatus.OK, dispatchRes.getStatusCode());
        assertEquals(OrderStatus.DISPATCHED, dispatchRes.getBody().status());

        OrderRejectReq rejectReq = new OrderRejectReq("Дүкөндө жок");
        HttpEntity<String> rejectHttpReq = new HttpEntity<>(objectMapper.writeValueAsString(rejectReq), adminHeaders);
        ResponseEntity<OrderDto> rejectRes = restTemplate.postForEntity("/api/drivers/orders/" + orderId + "/reject", rejectHttpReq, OrderDto.class);
        assertEquals(HttpStatus.OK, rejectRes.getStatusCode());

        PaymentCreateReq paymentReq = new PaymentCreateReq(
                UUID.randomUUID(),
                new BigDecimal("2000.00"),
                PaymentMethod.CASH,
                "Накталай эсептешүү, жарым-жартылай төлөм",
                UUID.randomUUID()
        );
        when(paymentService.createPayment(any(), any())).thenReturn(
                new IdempotentResponse<>(new PaymentDto(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("2000.00"), PaymentMethod.CASH, "Накталай эсептешүү, жарым-жартылай төлөм", UUID.randomUUID(), null, null, null), false)
        );

        HttpHeaders paymentHeaders = new HttpHeaders();
        paymentHeaders.putAll(adminHeaders);
        paymentHeaders.set("Idempotency-Key", UUID.randomUUID().toString());

        HttpEntity<String> paymentHttpReq = new HttpEntity<>(objectMapper.writeValueAsString(paymentReq), paymentHeaders);
        ResponseEntity<PaymentDto> paymentRes = restTemplate.postForEntity("/api/drivers/payments", paymentHttpReq, PaymentDto.class);
        assertEquals(HttpStatus.CREATED, paymentRes.getStatusCode());

        ResponseEntity<String> paymentsListRes = restTemplate.exchange("/api/drivers/payments", HttpMethod.GET, getReq, String.class);
        assertEquals(HttpStatus.OK, paymentsListRes.getStatusCode());

        UUID returnId = UUID.randomUUID();
        when(returnService.acceptReturn(any())).thenReturn(null);
        when(returnService.rejectReturn(any())).thenReturn(null);

        ResponseEntity<String> returnsListRes = restTemplate.exchange("/api/drivers/returns", HttpMethod.GET, getReq, String.class);
        assertEquals(HttpStatus.OK, returnsListRes.getStatusCode());

        ResponseEntity<String> acceptReturnRes = restTemplate.postForEntity("/api/drivers/returns/" + returnId + "/accept", getReq, String.class);
        assertEquals(HttpStatus.OK, acceptReturnRes.getStatusCode());

        ResponseEntity<String> rejectReturnRes = restTemplate.postForEntity("/api/drivers/returns/" + returnId + "/reject", getReq, String.class);
        assertEquals(HttpStatus.OK, rejectReturnRes.getStatusCode());

        System.out.println("✅ Full E2E Warehouse Manager workflow executed successfully!");
    }
}