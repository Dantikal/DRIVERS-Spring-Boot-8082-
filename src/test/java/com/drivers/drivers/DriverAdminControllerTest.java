package com.drivers.drivers;

import com.drivers.modules.drivers.controller.DriverAdminController;
import com.drivers.modules.drivers.dto.DriverDto;
import com.drivers.modules.drivers.dto.req.DriverCreateReq;
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
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DriverAdminControllerTest {

    @Mock
    private DriverService driverService;

    @InjectMocks
    private DriverAdminController driverAdminController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(driverAdminController)
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver()
                )
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
    void getDriver_shouldReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        DriverDto dto = DriverDto.builder().id(id).fullName("Driver").build();

        when(driverService.getDriver(id)).thenReturn(dto);

        mockMvc.perform(get("/api/drivers/{driverId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getAllDrivers_shouldReturn200() throws Exception {
        DriverDto dto = DriverDto.builder()
                .id(UUID.randomUUID()).fullName("Driver").build();

        Page<DriverDto> page = new PageImpl<>(List.of(dto), Pageable.ofSize(20), 1);

        when(driverService.getAllDrivers(
                any(Pageable.class),
                nullable(DriverStatus.class),
                nullable(UUID.class),
                nullable(String.class)
        )).thenReturn(page);

        mockMvc.perform(get("/api/drivers")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("Driver"));
    }
}
