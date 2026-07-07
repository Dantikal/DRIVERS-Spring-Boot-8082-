package com.drivers.integration;

import com.drivers.modules.auth.dto.req.LoginRequest;
import com.drivers.modules.auth.dto.res.LoginResponse;
import com.drivers.modules.auth.service.DriverAuthService;
import com.drivers.modules.drivers.service.DriverService;
import com.drivers.modules.orders.service.OrderService;
import com.drivers.modules.payments.service.PaymentService;
import com.drivers.modules.returns.service.ReturnService;
import com.drivers.shared.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class DriverEndpointsIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private DriverAuthService authService;

    @MockitoBean
    private DriverService driverService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private ReturnService returnService;

    private String validJwtToken;
    private HttpHeaders authHeaders;

    @BeforeEach
    void setUp() {
        UUID driverId = UUID.randomUUID();
        org.springframework.security.core.Authentication authentication =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "+996555123456",
                        null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_DRIVER"))
                );
        validJwtToken = jwtUtil.generateToken(authentication, driverId, UUID.randomUUID());

        authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(validJwtToken);
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("1. Happy Path: Should successfully login and return JWT token")
    void testHappyPath_Login() {
        LoginResponse mockResponse = new LoginResponse("mock-jwt-token-123", "Bearer", null);
        when(authService.login(any(LoginRequest.class))).thenReturn(mockResponse);

        String loginJson = "{\"phone\": \"+996555123456\", \"password\": \"secret\"}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(loginJson, headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/drivers/auth/login", request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"access\":\"mock-jwt-token-123\""));
    }

    @Test
    @DisplayName("2. Happy Path: Should get driver profile data (Requires Auth)")
    void testHappyPath_GetProfile() {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders);
        ResponseEntity<String> response = restTemplate.exchange("/api/drivers/me", HttpMethod.GET, request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("3. Happy Path: Should create driver order (Requires Auth)")
    void testHappyPath_CreateOrder() {
        when(orderService.createOrder(any(), any(), any()))
                .thenReturn(new com.drivers.shared.dto.IdempotentResponse<>(null, false));

        String validOrderJson = """
            {
                "warehouseId": "123e4567-e89b-12d3-a456-426614174000",
                "totalAmount": 1500.50,
                "comment": "Сынак үчүн тапшырык",
                "items": [
                    {
                        "productId": "123e4567-e89b-12d3-a456-426614174001",
                        "requestedQty": 10
                    }
                ]
            }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.putAll(authHeaders);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());

        HttpEntity<String> request = new HttpEntity<>(validOrderJson, headers);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/drivers/me/orders", request, String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful(), "Expected 2xx but got " + response.getStatusCode());
    }

    @Test
    @DisplayName("4. Happy Path: Should get driver payments (Requires Auth)")
    void testHappyPath_GetPayments() {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders);
        ResponseEntity<String> response = restTemplate.exchange("/api/drivers/me/payments", HttpMethod.GET, request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("5. Unhappy Path: Accessing protected endpoint without JWT should return 401/403")
    void testUnhappyPath_UnauthorizedAccess() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/drivers/me", String.class);

        assertTrue(response.getStatusCode() == HttpStatus.UNAUTHORIZED || response.getStatusCode() == HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("6. Unhappy Path: Login with missing credentials should trigger 400 Validation Error")
    void testUnhappyPath_LoginValidation() {
        String invalidLoginJson = "{\"phone\": \"\", \"password\": \"\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(invalidLoginJson, headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/drivers/auth/login", request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("7. Unhappy Path: Order creation with negative amount should trigger 400 Validation Error")
    void testUnhappyPath_OrderNegativeAmount() {
        String invalidOrderJson = """
            {
                "warehouseId": "123e4567-e89b-12d3-a456-426614174000",
                "totalAmount": -50.00,
                "items": []
            }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.putAll(authHeaders);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());

        HttpEntity<String> request = new HttpEntity<>(invalidOrderJson, headers);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/drivers/me/orders", request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("8. Unhappy Path: Payment access with POST method should trigger 405 Method Not Allowed")
    void testUnhappyPath_PaymentInvalidMethod() {
        String invalidPaymentJson = "{}";
        HttpEntity<String> request = new HttpEntity<>(invalidPaymentJson, authHeaders);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/drivers/me/payments", request, String.class);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
    }
}