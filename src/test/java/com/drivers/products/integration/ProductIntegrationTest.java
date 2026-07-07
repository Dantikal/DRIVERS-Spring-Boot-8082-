package com.drivers.products.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Test for Product Proxy flow.
 * Tests the entire slice: Controller -> Service -> RestTemplate -> Mock External Server.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // Disable security filters for isolated controller testing
@ActiveProfiles("dev")
@WithMockUser(roles = "DRIVER")
public class ProductIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @Value("${external.factory-service.url}")
    private String factoryServiceUrl;

    @Value("${external.factory-service.products-path}")
    private String productsPath;

    @Value("${external.factory-service.x-api-key}")
    private String apiKey;

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    @DisplayName("Should successfully proxy products and STRIP upstream headers (Prevent 502 Bad Gateway)")
    void shouldReturnProducts_andStripHeaders_whenUpstreamSuccess() throws Exception {
        String url = factoryServiceUrl + productsPath;
        String mockResponseBody = "{\"count\": 1, \"results\": [{\"name\": \"Товар\"}]}";

        org.springframework.http.HttpHeaders responseHeaders = new org.springframework.http.HttpHeaders();
        responseHeaders.add("Transfer-Encoding", "chunked");

        mockServer.expect(MockRestRequestMatchers.requestTo(url))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andExpect(MockRestRequestMatchers.header("X-API-KEY", apiKey))
                .andRespond(MockRestResponseCreators.withSuccess(mockResponseBody, MediaType.APPLICATION_JSON)
                        .headers(responseHeaders)
                );

        mockMvc.perform(get("/api/drivers/products")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(mockResponseBody))
                .andExpect(header().doesNotExist("Transfer-Encoding")); // Crucial assertion: ensure header is stripped!

        mockServer.verify();
    }

    @Test
    @DisplayName("Should transparently forward 401 Unauthorized from factory-service")
    void shouldForward401_whenUpstreamReturnsUnauthorized() throws Exception {
        String url = factoryServiceUrl + productsPath;
        String errorBody = "{\"error\": {\"code\": \"unauthorized\"}}";

        mockServer.expect(MockRestRequestMatchers.requestTo(url))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.UNAUTHORIZED)
                        .body(errorBody)
                        .contentType(MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/drivers/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(errorBody));

        mockServer.verify();
    }

    @Test
    @DisplayName("Should fallback to 500 Internal Server Error if factory-service is unreachable")
    void shouldReturn500_whenUpstreamIsDown() throws Exception {
        String url = factoryServiceUrl + productsPath;

        mockServer.expect(MockRestRequestMatchers.requestTo(url))
                .andRespond((request) -> {
                    throw new java.net.ConnectException("Connection refused");
                });

        mockMvc.perform(get("/api/drivers/products"))
                .andExpect(status().isInternalServerError());

        mockServer.verify();
    }
}
