package com.drivers.products.service;

import com.drivers.modules.products.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceImplTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(productService, "factoryServiceUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(productService, "productsPath", "/api/products");
    }

    @Test
    void getActiveProducts_shouldReturnProducts_whenRequestIsSuccessful() {
        String token = "Bearer some-jwt-token";
        String expectedResponse = "[{\"name\": \"Product A\"}]";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, token);
        HttpEntity<Void> expectedEntity = new HttpEntity<>(headers);

        when(restTemplate.exchange(
                eq("http://localhost:8080/api/products"),
                eq(HttpMethod.GET),
                eq(expectedEntity),
                eq(Object.class)
        )).thenReturn(ResponseEntity.ok(expectedResponse));

        ResponseEntity<Object> response = productService.getActiveProducts(token);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expectedResponse, response.getBody());
    }

    @Test
    void getActiveProducts_shouldThrowException_whenRestTemplateFails() {
        String token = "Bearer some-jwt-token";
        
        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenThrow(new RuntimeException("Connection refused"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            productService.getActiveProducts(token);
        });

        assertEquals("Не удалось получить список товаров из factory-service", exception.getMessage());
    }
}
