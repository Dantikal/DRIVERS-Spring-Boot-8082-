package com.drivers.drivers;

import com.drivers.modules.drivers.controller.DriverController;
import com.drivers.modules.drivers.dto.DriverDebtDto;
import com.drivers.modules.drivers.dto.DriverDto;
import com.drivers.modules.drivers.dto.req.DriverCreateReq;
import com.drivers.modules.drivers.dto.req.DriverUpdateReq;
import com.drivers.modules.drivers.entity.DriverStatus;
import com.drivers.modules.drivers.service.DriverService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@ExtendWith(MockitoExtension.class)
class DriverControllerTest {

    @Mock
    private DriverService driverService;

    @InjectMocks
    private DriverController driverController;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(driverController)
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void createDriver_WithNullWarehouse_ShouldReturnBadRequest() throws Exception {
        DriverCreateReq req = DriverCreateReq.builder()
                .fullName("Ye ye")
                .phone("+996707123456")
                .password("password")
                .carNumber("01 730 AZX")
                .warehouseId(null)
                .build();

        mockMvc.perform(post("/api/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
    @Test
    void updateDriver_shouldReturn200() throws Exception {

        UUID id = UUID.randomUUID();

        DriverDto dto = DriverDto.builder()
                .id(id)
                .fullName("Updated Name")
                .build();

        DriverUpdateReq req = DriverUpdateReq.builder()
                .fullName("Updatedname")
                .phone("+996777123456")
                .carNumber("01 730 AZX")
                .warehouseId(UUID.randomUUID())
                .status(DriverStatus.ACTIVE)
                .build();

        when(driverService.updateDriver(eq(id), any())).thenReturn(dto);

        mockMvc.perform(put("/api/drivers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.fullName").value("Updated Name"));
    }
    @Test
    void getDriver_shouldReturn200() throws Exception {

        UUID id = UUID.randomUUID();

        DriverDto dto = DriverDto.builder()
                .id(id)
                .fullName("Driver")
                .build();

        when(driverService.getDriver(id)).thenReturn(dto);

        mockMvc.perform(get("/api/drivers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getAllDrivers_shouldReturn200() throws Exception {
        DriverDto dto = DriverDto.builder()
                .id(UUID.randomUUID())
                .fullName("Driver")
                .build();

        List<DriverDto> dtoList = new ArrayList<>(List.of(dto));
        Pageable pageable = Pageable.ofSize(20);
        Page<DriverDto> page = new PageImpl<>(dtoList, pageable, 1);

        when(driverService.getAllDrivers(
                any(Pageable.class),
                org.mockito.ArgumentMatchers.nullable(com.drivers.modules.drivers.entity.DriverStatus.class),
                org.mockito.ArgumentMatchers.nullable(UUID.class),
                org.mockito.ArgumentMatchers.nullable(String.class)
        )).thenReturn(page);

        mockMvc.perform(get("/api/drivers")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("Driver"));
    }

    @Test
    void getDriverDebt_shouldReturn200() throws Exception {

        UUID id = UUID.randomUUID();

        DriverDebtDto dto = DriverDebtDto.builder()
                .driverId(id)
                .totalDebt(BigDecimal.ZERO)
                .build();

        when(driverService.getDriverDebt(id)).thenReturn(dto);

        mockMvc.perform(get("/api/drivers/{id}/debt", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value(id.toString()));
    }
    @Test
    void getAllDebts_shouldReturn200() throws Exception {

        DriverDebtDto dto = DriverDebtDto.builder()
                .driverId(UUID.randomUUID())
                .totalDebt(BigDecimal.TEN)
                .build();

        Pageable pageable = Pageable.ofSize(20);
        Page<DriverDebtDto> page = new PageImpl<>(List.of(dto), pageable, 1);

        when(driverService.getAllDebts(any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/drivers/debts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].totalDebt").value(10));
    }
}