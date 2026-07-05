package com.drivers.products.controller;

import com.drivers.modules.products.controller.ProductController;
import com.drivers.modules.products.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class ProductControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController productController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(productController).build();
    }

    @Test
    void getProducts_shouldReturnProducts_whenTokenProvided() throws Exception {
        String token = "Bearer some-jwt-token";
        String expectedResponse = "[{\"id\": 1, \"name\": \"Product 1\"}]";
        
        when(productService.getActiveProducts(token)).thenReturn(ResponseEntity.ok(expectedResponse));

        mockMvc.perform(get("/api/drivers/products")
                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedResponse));

        verify(productService, times(1)).getActiveProducts(token);
    }
    
    @Test
    void getProducts_shouldReturnProducts_whenNoTokenProvided() throws Exception {
        String expectedResponse = "[{\"id\": 1, \"name\": \"Product 1\"}]";
        
        when(productService.getActiveProducts(null)).thenReturn(ResponseEntity.ok(expectedResponse));

        mockMvc.perform(get("/api/drivers/products"))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedResponse));

        verify(productService, times(1)).getActiveProducts(null);
    }
}
