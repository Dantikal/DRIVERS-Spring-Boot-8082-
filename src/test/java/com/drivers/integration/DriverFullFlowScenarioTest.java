package com.drivers.integration;

import com.drivers.modules.auth.dto.res.LoginResponse;
import com.drivers.modules.drivers.service.DriverService;
import com.drivers.modules.orders.dto.OrderDto;
import com.drivers.modules.orders.dto.req.OrderCreateReq;
import com.drivers.modules.orders.dto.req.OrderItemReq;
import com.drivers.modules.orders.entity.OrderStatus;
import com.drivers.modules.orders.service.OrderService;
import com.drivers.modules.returns.dto.req.ReturnCreateReq;
import com.drivers.modules.returns.dto.req.ReturnItemReq;
import com.drivers.modules.returns.entity.ReturnReason;
import com.drivers.modules.returns.service.ReturnService;
import com.drivers.shared.dto.IdempotentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class DriverFullFlowScenarioTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DriverService driverService;
    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private ReturnService returnService;
    @MockitoBean
    private com.drivers.modules.auth.service.DriverAuthService authService;

    @Autowired
    private com.drivers.shared.util.JwtUtil jwtUtil;

    @Test
    @DisplayName("Full Driver E2E Flow: Login -> Profile -> Order -> Return")
    void executeFullDriverLifecycle() throws Exception {
        UUID driverId = UUID.randomUUID();
        String validPhone = "+996555123456";

        org.springframework.security.core.Authentication authentication =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        validPhone,
                        null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_DRIVER"))
                );
        String validToken = jwtUtil.generateToken(authentication, driverId, UUID.randomUUID());

        when(authService.login(any())).thenReturn(new LoginResponse(validToken, "Bearer", null));

        String loginJson = "{\"phone\": \"+996555123456\", \"password\": \"secret\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> loginRequest = new HttpEntity<>(loginJson, headers);
        ResponseEntity<LoginResponse> loginRes = restTemplate.postForEntity("/api/drivers/auth/login", loginRequest, LoginResponse.class);

        assertEquals(HttpStatus.OK, loginRes.getStatusCode());
        assertNotNull(loginRes.getBody());
        String accessToken = loginRes.getBody().accessToken();
        assertNotNull(accessToken);

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> profileReq = new HttpEntity<>(authHeaders);
        ResponseEntity<String> profileRes = restTemplate.exchange("/api/drivers/me", HttpMethod.GET, profileReq, String.class);
        assertEquals(HttpStatus.OK, profileRes.getStatusCode());

        OrderDto createdOrder = new OrderDto(
                null, null, null,
                OrderStatus.NEW,
                null, null, null, null, null, null
        );
        when(orderService.createOrder(any(), any(), any())).thenReturn(new IdempotentResponse<>(createdOrder, false));

        OrderCreateReq orderReq = new OrderCreateReq(
                UUID.randomUUID(),
                new BigDecimal("1000.00"),
                "Тезирээк жеткирип бериңиз",
                List.of(new OrderItemReq(UUID.randomUUID(), 10, null))
        );

        HttpHeaders orderHeaders = new HttpHeaders();
        orderHeaders.putAll(authHeaders);
        orderHeaders.set("Idempotency-Key", UUID.randomUUID().toString());

        HttpEntity<String> orderHttpReq = new HttpEntity<>(objectMapper.writeValueAsString(orderReq), orderHeaders);
        ResponseEntity<OrderDto> orderRes = restTemplate.postForEntity("/api/drivers/me/orders", orderHttpReq, OrderDto.class);

        assertTrue(orderRes.getStatusCode().is2xxSuccessful());
        assertEquals(OrderStatus.NEW, orderRes.getBody().status());

        ResponseEntity<String> ordersListRes = restTemplate.exchange("/api/drivers/me/orders?status=NEW", HttpMethod.GET, profileReq, String.class);
        assertEquals(HttpStatus.OK, ordersListRes.getStatusCode());

        // Modify order step
        UUID targetOrderId = UUID.randomUUID();
        OrderDto modifiedOrder = new OrderDto(
                targetOrderId, driverId, null,
                OrderStatus.MODIFIED,
                null, null, null, null, null, null
        );
        when(orderService.modifyMyOrder(any(), any(), any())).thenReturn(modifiedOrder);

        com.drivers.modules.orders.dto.req.OrderModifyReq modifyReq = com.drivers.modules.orders.dto.req.OrderModifyReq.builder()
                .totalAmount(new BigDecimal("2000.00"))
                .items(List.of(
                        new com.drivers.modules.orders.dto.req.OrderItemReq(UUID.randomUUID(), 15, null)
                ))
                .build();

        HttpEntity<String> modifyHttpReq = new HttpEntity<>(objectMapper.writeValueAsString(modifyReq), authHeaders);
        ResponseEntity<OrderDto> modifyRes = restTemplate.exchange("/api/drivers/me/orders/" + targetOrderId, HttpMethod.PUT, modifyHttpReq, OrderDto.class);

        assertEquals(HttpStatus.OK, modifyRes.getStatusCode());
        assertEquals(OrderStatus.MODIFIED, modifyRes.getBody().status());

        ReturnCreateReq returnReq = new ReturnCreateReq(
                driverId,
                new BigDecimal("200.00"),
                List.of(new ReturnItemReq(UUID.randomUUID(), 1, 0, ReturnReason.DEFECT, null))
        );

        when(returnService.createReturn(any(), any(), any())).thenReturn(new IdempotentResponse<>(null, false));

        HttpHeaders returnHeaders = new HttpHeaders();
        returnHeaders.putAll(authHeaders);
        returnHeaders.set("Idempotency-Key", UUID.randomUUID().toString());

        HttpEntity<String> returnHttpReq = new HttpEntity<>(objectMapper.writeValueAsString(returnReq), returnHeaders);
        ResponseEntity<String> returnRes = restTemplate.postForEntity("/api/drivers/returns/me", returnHttpReq, String.class);

        assertTrue(returnRes.getStatusCode().is2xxSuccessful());

        System.out.println("✅ Full E2E Driver flow executed successfully!");
    }
}