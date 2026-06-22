package com.drivers.payments;

import com.drivers.config.JwtAuthFilter;
import com.drivers.modules.payments.controller.PaymentSelfController;
import com.drivers.modules.payments.dto.PaymentDto;
import com.drivers.modules.payments.entity.PaymentMethod;
import com.drivers.modules.payments.service.PaymentService;
import com.drivers.shared.util.CurrentDriverId;
import com.drivers.shared.util.CustomUserDetailsService;
import com.drivers.shared.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
        controllers = PaymentSelfController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthFilter.class
        )
)
@EnableMethodSecurity
class PaymentSelfControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

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

    @Test
    @WithMockUser(roles = "DRIVER")
    void getPayments_WithDriverRole_ShouldReturn200() throws Exception {
        PaymentDto dto = PaymentDto.builder()
                .id(UUID.randomUUID())
                .amount(BigDecimal.valueOf(5000))
                .paymentMethod(PaymentMethod.CASH)
                .build();

        when(paymentService.getPayments(any(Pageable.class), eq(STUB_DRIVER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/drivers/me/payments")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value(5000));
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void getPaymentById_WithDriverRole_ShouldReturn200() throws Exception {
        UUID paymentId = UUID.randomUUID();
        PaymentDto dto = PaymentDto.builder()
                .id(paymentId)
                .amount(BigDecimal.valueOf(1500))
                .build();

        when(paymentService.getPayment(eq(paymentId), eq(STUB_DRIVER_ID))).thenReturn(dto);

        mockMvc.perform(get("/api/drivers/me/payments/{id}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.amount").value(1500));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void getPayments_WithAdminRole_ShouldReturn403Forbidden() throws Exception {
        mockMvc.perform(get("/api/drivers/me/payments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void createPayment_PostMethod_ShouldReturn405MethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/drivers/me/payments")
                        .with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }
}