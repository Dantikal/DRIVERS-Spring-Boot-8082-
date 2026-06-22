package com.drivers.payments;

import com.drivers.config.JwtAuthFilter;
import com.drivers.modules.payments.controller.PaymentAdminController;
import com.drivers.modules.payments.dto.PaymentDto;
import com.drivers.modules.payments.dto.req.PaymentCreateReq;
import com.drivers.modules.payments.entity.PaymentMethod;
import com.drivers.modules.payments.service.PaymentService;
import com.drivers.shared.util.CustomUserDetailsService;
import com.drivers.shared.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = PaymentAdminController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthFilter.class
        )
)
@EnableMethodSecurity
class PaymentAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void createPayment_WhenFirstTime_ShouldReturn201AndNoHeader() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentCreateReq req = new PaymentCreateReq(
                UUID.randomUUID(), BigDecimal.valueOf(1000), PaymentMethod.CASH, "Test", UUID.randomUUID()
        );

        PaymentDto freshDto = PaymentDto.builder()
                .id(UUID.randomUUID())
                .amount(BigDecimal.valueOf(1000))
                .build();

        // Мокаем создание
        when(paymentService.createPayment(any(PaymentCreateReq.class), eq(idempotencyKey)))
                .thenReturn(freshDto);
        // Мокаем проверку на старость (заказ свежий)
        when(paymentService.checkIfThisPaymentWasAlreadyCreated(any(PaymentDto.class)))
                .thenReturn(false);

        mockMvc.perform(post("/api/drivers/payments")
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(header().doesNotExist("Idempotency-Replayed")); // Хедера быть не должно
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void createPayment_WhenReplayed_ShouldReturn201AndHeader() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentCreateReq req = new PaymentCreateReq(
                UUID.randomUUID(), BigDecimal.valueOf(1000), PaymentMethod.CASH, "Test", UUID.randomUUID()
        );

        PaymentDto replayedDto = PaymentDto.builder()
                .id(UUID.randomUUID())
                .amount(BigDecimal.valueOf(1000))
                .build();

        when(paymentService.createPayment(any(PaymentCreateReq.class), eq(idempotencyKey)))
                .thenReturn(replayedDto);
        // Заказ старый (повторный клик)
        when(paymentService.checkIfThisPaymentWasAlreadyCreated(any(PaymentDto.class)))
                .thenReturn(true);

        mockMvc.perform(post("/api/drivers/payments")
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(header().string("Idempotency-Replayed", "true")); // Хедер ДОЛЖЕН быть
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void createPayment_WhenNoHeader_ShouldReturn400BadRequest() throws Exception {
        PaymentCreateReq req = new PaymentCreateReq(
                UUID.randomUUID(), BigDecimal.valueOf(1000), PaymentMethod.CASH, "Test", UUID.randomUUID()
        );

        // Без хедера Idempotency-Key Спринг автоматически вернет 400 Bad Request
        mockMvc.perform(post("/api/drivers/payments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void createPayment_WithDriverRole_ShouldReturn403Forbidden() throws Exception {
        PaymentCreateReq req = new PaymentCreateReq(
                UUID.randomUUID(), BigDecimal.valueOf(1000), PaymentMethod.CASH, "Test", UUID.randomUUID()
        );

        mockMvc.perform(post("/api/drivers/payments")
                        .with(csrf())
                        .header("Idempotency-Key", "some-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void getAllPayments_WithAdminRole_ShouldReturn200() throws Exception {
        PaymentDto dto = PaymentDto.builder().id(UUID.randomUUID()).build();

        when(paymentService.getPayments(any(Pageable.class), any(), any()))
                .thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/drivers/payments")
                        .param("page", "0")
                        .param("size", "20")
                        .param("paymentMethod", "CASH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").exists());
    }
}