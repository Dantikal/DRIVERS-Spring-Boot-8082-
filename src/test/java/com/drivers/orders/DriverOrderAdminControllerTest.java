package com.drivers.orders;

import com.drivers.modules.orders.controller.DriverOrderAdminController;
import com.drivers.modules.orders.dto.OrderDto;
import com.drivers.modules.orders.dto.req.OrderItemReq;
import com.drivers.modules.orders.dto.req.OrderModifyReq;
import com.drivers.modules.orders.dto.req.OrderRejectReq;
import com.drivers.modules.orders.entity.OrderStatus;
import com.drivers.modules.orders.service.OrderService;
import com.drivers.shared.util.CustomUserDetailsService;
import com.drivers.shared.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

@WebMvcTest(DriverOrderAdminController.class)
@EnableMethodSecurity
public class DriverOrderAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    // Мокаем зависимости для фильтра, НО НЕ сам JwtAuthFilter!
    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private UUID orderId;
    private OrderDto orderDto;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        orderDto = OrderDto.builder()
                .id(orderId)
                .driverId(UUID.randomUUID())
                .warehouseId(UUID.randomUUID())
                .status(OrderStatus.NEW)
                .totalAmount(BigDecimal.valueOf(1000))
                .comment("Warehouse test")
                .items(List.of())
                .build();
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void getAllOrders_AsManager_ShouldReturnPagedOrders() throws Exception {
        PageImpl<OrderDto> page = new PageImpl<>(List.of(orderDto), PageRequest.of(0, 20), 1);
        when(orderService.getOrders(any(Pageable.class), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/drivers/orders")
                        .param("page", "0")
                        .param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(orderId.toString()))
                .andExpect(jsonPath("$.content[0].comment").value("Warehouse test"));
    }

    @Test
    @WithMockUser(roles = "DRIVER") // Водитель ломится на склад -> должен получить 403
    void getAllOrders_AsDriver_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/drivers/orders")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void getOrder_ShouldReturnOrder() throws Exception {
        when(orderService.getOrder(orderId)).thenReturn(orderDto);

        mockMvc.perform(get("/api/drivers/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void confirmOrder_ShouldReturnConfirmedOrder() throws Exception {
        OrderDto confirmedDto = OrderDto.builder()
                .id(orderId)
                .status(OrderStatus.CONFIRMED)
                .build();

        when(orderService.confirmOrder(orderId)).thenReturn(confirmedDto);

        mockMvc.perform(post("/api/drivers/orders/{id}/confirm", orderId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void modifyOrder_WithValidBody_ShouldReturnModifiedOrder() throws Exception {
        OrderItemReq req = OrderItemReq.builder()
                .productId(UUID.randomUUID())
                .requestedQty(1)
                .approvedQty(1)
                .build();
        OrderModifyReq modifyReq = new OrderModifyReq(BigDecimal.valueOf(2000), "Valid comment", List.of(req));
        OrderDto modifiedDto = OrderDto.builder()
                .id(orderId)
                .status(OrderStatus.MODIFIED)
                .totalAmount(BigDecimal.valueOf(2000))
                .comment("Valid comment")
                .build();

        when(orderService.modifyOrder(eq(orderId), any(OrderModifyReq.class))).thenReturn(modifiedDto);

        mockMvc.perform(post("/api/drivers/orders/{id}/modify", orderId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(modifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MODIFIED"))
                .andExpect(jsonPath("$.totalAmount").value(2000));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void rejectOrder_ShouldReturnRejectedOrder() throws Exception {
        OrderRejectReq rejectReq = new OrderRejectReq("No stock");
        OrderDto rejectedDto = OrderDto.builder()
                .id(orderId)
                .status(OrderStatus.REJECTED)
                .comment("No stock")
                .build();

        when(orderService.rejectOrder(eq(orderId), any(OrderRejectReq.class))).thenReturn(rejectedDto);

        mockMvc.perform(post("/api/drivers/orders/{id}/reject", orderId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}