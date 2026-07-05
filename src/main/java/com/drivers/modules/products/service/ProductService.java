package com.drivers.modules.products.service;

import org.springframework.http.ResponseEntity;

public interface ProductService {
    ResponseEntity<Object> getActiveProducts(String authorizationHeader );
}
