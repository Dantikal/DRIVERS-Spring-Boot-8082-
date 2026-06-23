package com.drivers.returns;

import com.drivers.config.JwtAuthFilter;
import com.drivers.modules.returns.controller.ReturnSelfController;
import com.drivers.modules.returns.dto.PhotoUploadResponse;
import com.drivers.modules.returns.dto.ReturnRequestDto;
import com.drivers.modules.returns.dto.req.ReturnCreateReq;
import com.drivers.modules.returns.dto.req.ReturnItemReq;
import com.drivers.modules.returns.entity.ReturnStatus;
import com.drivers.modules.returns.service.ReturnService;
import com.drivers.shared.dto.IdempotentResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = ReturnSelfController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthFilter.class
        )
)
@EnableMethodSecurity
class ReturnSelfControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private ReturnService returnService;

    private static final UUID STUB_DRIVER_ID = UUID.randomUUID();
    private ReturnRequestDto returnDto;

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
        returnDto = ReturnRequestDto.builder()
                .id(UUID.randomUUID())
                .driverId(STUB_DRIVER_ID)
                .returnedAt(Instant.now())
                .totalAmount(BigDecimal.valueOf(500))
                .status(ReturnStatus.PENDING)
                .items(List.of())
                .build();
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void getMyReturns_Success() throws Exception {
        Page<ReturnRequestDto> page = new PageImpl<>(List.of(returnDto));
        when(returnService.getReturns(any(Pageable.class), eq(STUB_DRIVER_ID), any())).thenReturn(page);

        mockMvc.perform(get("/api/drivers/returns/me")
                        .param("status", "PENDING")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(returnDto.id().toString()));
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void createReturn_WhenNew_Returns201() throws Exception {
        ReturnItemReq item = new ReturnItemReq(UUID.randomUUID(), 1, 0, com.drivers.modules.returns.entity.ReturnReason.NOT_SOLD, null);
        ReturnCreateReq req = new ReturnCreateReq(STUB_DRIVER_ID, BigDecimal.valueOf(500), List.of(item)); // <-- Передаем товар

        IdempotentResponse<ReturnRequestDto> serviceResponse = new IdempotentResponse<>(returnDto, false);

        when(returnService.createReturn(any(), eq(STUB_DRIVER_ID), eq("idempotency-123")))
                .thenReturn(serviceResponse);

        mockMvc.perform(post("/api/drivers/returns/me")
                        .header("Idempotency-Key", "idempotency-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.id").value(returnDto.id().toString()));
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void createReturn_WhenReplayed_Returns200AndHeader() throws Exception {
        ReturnItemReq item = new ReturnItemReq(UUID.randomUUID(), 1, 0, com.drivers.modules.returns.entity.ReturnReason.NOT_SOLD, null);
        ReturnCreateReq req = new ReturnCreateReq(STUB_DRIVER_ID, BigDecimal.valueOf(500), List.of(item)); // <-- Передаем товар

        IdempotentResponse<ReturnRequestDto> serviceResponse = new IdempotentResponse<>(returnDto, true);

        when(returnService.createReturn(any(), eq(STUB_DRIVER_ID), eq("idempotency-123")))
                .thenReturn(serviceResponse);

        mockMvc.perform(post("/api/drivers/returns/me")
                        .header("Idempotency-Key", "idempotency-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(returnDto.id().toString()));
    }
    @Test
    @WithMockUser(roles = "DRIVER")
    void getReturn_Success() throws Exception {
        UUID returnId = returnDto.id();
        when(returnService.getReturn(eq(returnId), eq(STUB_DRIVER_ID))).thenReturn(returnDto);

        mockMvc.perform(get("/api/drivers/returns/me/{id}", returnId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(returnId.toString()));
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void uploadPhoto_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "image".getBytes());
        PhotoUploadResponse res = new PhotoUploadResponse("/uploads/returns/test.jpg");

        when(returnService.uploadPhoto(any())).thenReturn(res);

        mockMvc.perform(multipart("/api/drivers/returns/me/upload-photo")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("/uploads/returns/test.jpg"));
    }
}