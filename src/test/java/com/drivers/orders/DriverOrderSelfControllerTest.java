package com.drivers.orders;

import com.drivers.config.JwtAuthFilter;
import com.drivers.modules.orders.controller.DriverOrderSelfController;
import com.drivers.modules.orders.dto.OrderDto;
import com.drivers.modules.orders.dto.req.OrderCreateReq;
import com.drivers.modules.orders.dto.req.OrderItemReq;
import com.drivers.modules.orders.entity.OrderStatus;
import com.drivers.modules.orders.service.OrderService;
import com.drivers.shared.dto.IdempotentResponse;
import com.drivers.shared.util.CurrentDriverId;
import com.drivers.shared.util.CustomUserDetailsService;
import com.drivers.shared.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = DriverOrderSelfController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthFilter.class
        )
)
@EnableMethodSecurity
public class DriverOrderSelfControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private UUID orderId;
    private OrderDto orderDto;

    private static final UUID STUB_DRIVER_ID = UUID.randomUUID();

    @TestConfiguration
    static class WebConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.hasParameterAnnotation(CurrentDriverId.class);
                }

                @Override
                public Object resolveArgument(MethodParameter parameter,
                                              ModelAndViewContainer mavContainer,
                                              NativeWebRequest webRequest,
                                              WebDataBinderFactory binderFactory) {
                    return STUB_DRIVER_ID;
                }
            });
        }
    }

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        orderDto = OrderDto.builder()
                .id(orderId)
                .driverId(STUB_DRIVER_ID)
                .warehouseId(UUID.randomUUID())
                .status(OrderStatus.NEW)
                .totalAmount(BigDecimal.valueOf(700))
                .comment("Driver order test")
                .items(List.of())
                .build();
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void createOrder_WithInvalidBody_ShouldReturn400BadRequest() throws Exception {
        OrderCreateReq invalidReq = new OrderCreateReq(
                null, BigDecimal.valueOf(-100), "", List.of());

        mockMvc.perform(post("/api/drivers/me/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void getMyOrders_WithFilters_ShouldReturnFilteredPagedOrders() throws Exception {
        PageImpl<OrderDto> page = new PageImpl<>(List.of(orderDto), PageRequest.of(0, 20), 1);

        when(orderService.getOrders(any(Pageable.class), eq(STUB_DRIVER_ID), eq(OrderStatus.NEW)))
                .thenReturn(page);

        mockMvc.perform(get("/api/drivers/me/orders")
                        .param("page", "0")
                        .param("size", "20")
                        .param("status", "NEW")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(orderId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("NEW"));
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void getMyOrder_ShouldReturnSpecificOrder() throws Exception {
        when(orderService.getOrder(eq(orderId), eq(STUB_DRIVER_ID))).thenReturn(orderDto);

        mockMvc.perform(get("/api/drivers/me/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void createOrder_WithWrongRole_ShouldReturn403() throws Exception {
        OrderItemReq item = new OrderItemReq(UUID.randomUUID(), 3, null);
        OrderCreateReq req = new OrderCreateReq(
                UUID.randomUUID(), BigDecimal.valueOf(500), "test", List.of(item));

        mockMvc.perform(post("/api/drivers/me/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "test-idempotency-key")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void createOrder_WhenIdempotencyKeyIsMissing_ShouldReturn400() throws Exception {
        OrderItemReq item = new OrderItemReq(UUID.randomUUID(), 3, null);
        OrderCreateReq req = new OrderCreateReq(
                UUID.randomUUID(), BigDecimal.valueOf(500), "test", List.of(item));
        mockMvc.perform(post("/api/drivers/me/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void createOrder_WithIdempotencyKey_ShouldReturn201() throws Exception {
        OrderItemReq item = new OrderItemReq(UUID.randomUUID(), 3, null);
        OrderCreateReq req = new OrderCreateReq(UUID.randomUUID(), BigDecimal.valueOf(500), "test", List.of(item));
        String idempotencyKey = UUID.randomUUID().toString();

        when(orderService.createOrder(any(OrderCreateReq.class), eq(STUB_DRIVER_ID), eq(idempotencyKey)))
                .thenReturn(new IdempotentResponse<>(orderDto, false));

        mockMvc.perform(post("/api/drivers/me/orders")
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void createOrder_WhenFirstTime_ShouldReturn201AndNoHeader() throws Exception {
        OrderItemReq item = new OrderItemReq(UUID.randomUUID(), 3, null);
        OrderCreateReq req = new OrderCreateReq(UUID.randomUUID(), BigDecimal.valueOf(500), "test", List.of(item));
        String idempotencyKey = UUID.randomUUID().toString();

        OrderDto freshDto = OrderDto.builder()
                .id(orderId)
                .driverId(STUB_DRIVER_ID)
                .warehouseId(UUID.randomUUID())
                .status(OrderStatus.NEW)
                .totalAmount(BigDecimal.valueOf(700))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .comment("Driver order test")
                .items(List.of())
                .build();

        when(orderService.createOrder(any(OrderCreateReq.class), eq(STUB_DRIVER_ID), eq(idempotencyKey)))
                .thenReturn(new IdempotentResponse<>(freshDto, false));

        mockMvc.perform(post("/api/drivers/me/orders")
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(header().doesNotExist("Idempotency-Replayed"));
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void createOrder_WhenIdempotencyReplayed_ShouldReturn201AndHeader() throws Exception {
        OrderItemReq item = new OrderItemReq(UUID.randomUUID(), 3, null);
        OrderCreateReq req = new OrderCreateReq(UUID.randomUUID(), BigDecimal.valueOf(500), "test", List.of(item));
        String idempotencyKey = UUID.randomUUID().toString();

        OrderDto replayedDto = OrderDto.builder()
                .id(orderId)
                .driverId(STUB_DRIVER_ID)
                .warehouseId(UUID.randomUUID())
                .status(OrderStatus.NEW)
                .totalAmount(BigDecimal.valueOf(700))
                .comment("Driver order test")
                .items(List.of())
                .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .updatedAt(Instant.now())
                .build();


        when(orderService.createOrder(any(OrderCreateReq.class), eq(STUB_DRIVER_ID), eq(idempotencyKey)))
                .thenReturn(new IdempotentResponse<>(replayedDto, true));

        mockMvc.perform(post("/api/drivers/me/orders")
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(header().string("Idempotency-Replayed", "true"));
    }
}