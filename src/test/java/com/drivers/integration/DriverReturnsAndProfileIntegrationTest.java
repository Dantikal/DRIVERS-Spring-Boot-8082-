package com.drivers.integration;

import com.drivers.modules.drivers.service.DriverService;
import com.drivers.modules.returns.dto.PhotoUploadResponse;
import com.drivers.modules.returns.dto.ReturnRequestDto;
import com.drivers.modules.returns.service.ReturnService;
import com.drivers.shared.dto.IdempotentResponse;
import com.drivers.shared.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class DriverReturnsAndProfileIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private DriverService driverService;

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
    @DisplayName("1. Happy Path: Should create driver return")
    void testHappyPath_CreateReturn() {
        when(returnService.createReturn(any(), any(), any()))
                .thenReturn(new IdempotentResponse<>(null, false));

        String validReturnJson = """
            {
                "driverId": "123e4567-e89b-12d3-a456-426614174000",
                "totalAmount": 500.50,
                "items": [
                    {
                        "productId": "123e4567-e89b-12d3-a456-426614174001",
                        "qtyBoxes": 2,
                        "qtyPieces": 0,
                        "reason": "DEFECT",
                        "photoUrl": "http://example.com/photo.jpg"
                    }
                ]
            }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.putAll(authHeaders);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());

        HttpEntity<String> request = new HttpEntity<>(validReturnJson, headers);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/drivers/returns/me", request, String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful(), "Expected 2xx but got " + response.getStatusCode());
    }

    @Test
    @DisplayName("2. Happy Path: Should upload photo (Multipart Data)")
    void testHappyPath_UploadPhoto() {
        when(returnService.uploadPhoto(any()))
                .thenReturn(new PhotoUploadResponse("http://mock-s3.com/photo.jpg"));

        HttpHeaders multipartHeaders = new HttpHeaders();
        multipartHeaders.setBearerAuth(validJwtToken);
        multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileResource = new ByteArrayResource("dummy image content".getBytes()) {
            @Override
            public String getFilename() {
                return "test-photo.jpg";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, multipartHeaders);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/drivers/returns/me/upload-photo", requestEntity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("photo.jpg"));
    }

    @Test
    @DisplayName("3. Unhappy Path: Return creation without items should fail validation (400)")
    void testUnhappyPath_CreateReturnValidation() {
        String invalidReturnJson = """
            {
                "driverId": "123e4567-e89b-12d3-a456-426614174000",
                "totalAmount": 500.50,
                "items": []
            }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.putAll(authHeaders);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());

        HttpEntity<String> request = new HttpEntity<>(invalidReturnJson, headers);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/drivers/returns/me", request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("4. Happy Path: Should update driver profile")
    void testHappyPath_UpdateProfile() {
        when(driverService.updateDriver(any(), any())).thenReturn(null);

        String validProfileUpdateJson = """
            {
                "fullName": "Айбек Осмонов",
                "carNumber": "01KG123ABC",
                "phone": "+996555123456",
                "warehouseId": "123e4567-e89b-12d3-a456-426614174000",
                "status": "ACTIVE"
            }
        """;

        HttpEntity<String> request = new HttpEntity<>(validProfileUpdateJson, authHeaders);

        ResponseEntity<String> response = restTemplate.exchange("/api/drivers/me", HttpMethod.PUT, request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("5. Unhappy Path: Should fail to update profile with invalid phone format (400)")
    void testUnhappyPath_UpdateProfileValidation() {
        String invalidProfileUpdateJson = """
            {
                "fullName": "Айбек Осмонов",
                "carNumber": "01KG123ABC",
                "phone": "invalid-phone-format",
                "warehouseId": "123e4567-e89b-12d3-a456-426614174000",
                "status": "ACTIVE"
            }
        """;

        HttpEntity<String> request = new HttpEntity<>(invalidProfileUpdateJson, authHeaders);

        ResponseEntity<String> response = restTemplate.exchange("/api/drivers/me", HttpMethod.PUT, request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}