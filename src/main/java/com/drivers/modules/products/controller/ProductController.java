package com.drivers.modules.products.controller;

import com.drivers.modules.products.service.ProductService;
import com.drivers.modules.products.service.impl.ProductServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Получение товаров через factory-service")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Получить список активных товаров", description = "Делает запрос к factory-service, проксируя токен")
    public ResponseEntity<Object> getProducts(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        
        return productService.getActiveProducts(authorizationHeader);
    }
}
