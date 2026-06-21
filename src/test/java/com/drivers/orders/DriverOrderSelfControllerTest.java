package com.drivers.orders;

import com.drivers.config.JwtAuthFilter;
import com.drivers.modules.orders.controller.DriverOrderSelfController;
import com.drivers.modules.orders.dto.OrderDto;
import com.drivers.modules.orders.dto.req.OrderCreateReq;
import com.drivers.modules.orders.dto.req.OrderItemReq;
import com.drivers.modules.orders.entity.OrderStatus;
import com.drivers.modules.orders.service.OrderService;
import com.drivers.shared.util.CurrentDriverId;
import com.drivers.shared.util.CustomUserDetailsService;
import com.drivers.shared.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void createOrder_WithValidBody_ShouldReturnCreated() throws Exception {
        OrderItemReq item = new OrderItemReq(UUID.randomUUID(), 5, null);
        OrderCreateReq createReq = new OrderCreateReq(
                UUID.randomUUID(), BigDecimal.valueOf(700), "Driver test", List.of(item));

        when(orderService.createOrder(any(OrderCreateReq.class), eq(STUB_DRIVER_ID)))
                .thenReturn(orderDto);

        mockMvc.perform(post("/api/drivers/me/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void createOrder_WithInvalidBody_ShouldReturn400BadRequest() throws Exception {
        // null, негативное значение, пустой массив
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
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}