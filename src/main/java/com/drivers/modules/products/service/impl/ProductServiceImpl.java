package com.drivers.modules.products.service.impl;

import com.drivers.modules.products.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final RestTemplate restTemplate;

    @Value("${external.factory-service.url:http://localhost:8080}")
    private String factoryServiceUrl;

    @Value("${external.factory-service.products-path:/api/products}")
    private String productsPath;

    public ResponseEntity<Object> getActiveProducts(String authorizationHeader) {
        String url = factoryServiceUrl + productsPath;
        log.info("Proxying request to factory-service for products: {}", url);

        HttpHeaders headers = new HttpHeaders();
        if (authorizationHeader != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        log.info(requestEntity.getHeaders().toString());

        try {
            return restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    Object.class
            );
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("Factory service returned error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error while fetching products from factory-service", e);
            throw new RuntimeException("Не удалось получить список товаров из factory-service", e);
        }
    }
}
