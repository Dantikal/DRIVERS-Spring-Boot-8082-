package com.drivers.returns;

import com.drivers.config.JwtAuthFilter;
import com.drivers.modules.returns.controller.ReturnAdminController;
import com.drivers.modules.returns.dto.ReturnRequestDto;
import com.drivers.modules.returns.entity.ReturnStatus;
import com.drivers.modules.returns.service.ReturnService;
import com.drivers.shared.util.CustomUserDetailsService;
import com.drivers.shared.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = ReturnAdminController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthFilter.class
        )
)
@EnableMethodSecurity
class ReturnAdminControllerTest {

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

    private UUID returnId;
    private ReturnRequestDto pendingDto;
    private ReturnRequestDto acceptedDto;

    @BeforeEach
    void setUp() {
        returnId = UUID.randomUUID();
        pendingDto = ReturnRequestDto.builder()
                .id(returnId)
                .driverId(UUID.randomUUID())
                .returnedAt(Instant.now())
                .totalAmount(BigDecimal.valueOf(100))
                .status(ReturnStatus.PENDING)
                .items(List.of())
                .build();

        acceptedDto = ReturnRequestDto.builder()
                .id(returnId)
                .driverId(pendingDto.driverId())
                .totalAmount(pendingDto.totalAmount())
                .status(ReturnStatus.ACCEPTED)
                .build();
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void getAllReturns_Success() throws Exception {
        Page<ReturnRequestDto> page = new PageImpl<>(List.of(pendingDto));
        when(returnService.getReturns(any(Pageable.class), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/drivers/returns")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(returnId.toString()));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void getReturnDetails_Success() throws Exception {
        when(returnService.getReturn(returnId)).thenReturn(pendingDto);

        mockMvc.perform(get("/api/drivers/returns/{id}", returnId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(returnId.toString()));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void acceptReturn_Success() throws Exception {
        when(returnService.acceptReturn(returnId)).thenReturn(acceptedDto);

        mockMvc.perform(post("/api/drivers/returns/{id}/accept", returnId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void rejectReturn_Success() throws Exception {
        ReturnRequestDto rejectedDto = ReturnRequestDto.builder()
                .id(returnId)
                .status(ReturnStatus.REJECTED)
                .build();

        when(returnService.rejectReturn(returnId)).thenReturn(rejectedDto);

        mockMvc.perform(post("/api/drivers/returns/{id}/reject", returnId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}