package com.drivers.drivers;

import com.drivers.modules.drivers.controller.DriverSelfController;
import com.drivers.modules.drivers.dto.DriverDebtDto;
import com.drivers.modules.drivers.dto.DriverDto;
import com.drivers.modules.drivers.dto.req.DriverUpdateReq;
import com.drivers.modules.drivers.entity.DriverStatus;
import com.drivers.modules.drivers.service.DriverService;
import com.drivers.shared.util.CurrentDriverId;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@ExtendWith(MockitoExtension.class)
class DriverSelfControllerTest {

    @Mock
    private DriverService driverService;

    @InjectMocks
    private DriverSelfController driverSelfController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final UUID DRIVER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(driverSelfController)
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        stubDriverIdResolver(DRIVER_ID)
                )
                .build();
        objectMapper = new ObjectMapper();
    }

    private HandlerMethodArgumentResolver stubDriverIdResolver(UUID driverId) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(CurrentDriverId.class);
            }
            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return driverId;
            }
        };
    }

    @Test
    void getMyProfile_shouldReturn200() throws Exception {
        DriverDto dto = DriverDto.builder()
                .id(DRIVER_ID).fullName("Driver").build();

        when(driverService.getDriver(DRIVER_ID)).thenReturn(dto);

        mockMvc.perform(get("/api/drivers/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DRIVER_ID.toString()));
    }

    @Test
    void getMyDebt_shouldReturn200() throws Exception {
        DriverDebtDto dto = DriverDebtDto.builder()
                .driverId(DRIVER_ID)
                .totalDebt(BigDecimal.ZERO)
                .build();

        when(driverService.getDriverDebt(DRIVER_ID)).thenReturn(dto);

        mockMvc.perform(get("/api/drivers/me/debt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value(DRIVER_ID.toString()));
    }

    @Test
    void updateMyProfile_shouldReturn200() throws Exception {
        DriverDto dto = DriverDto.builder()
                .id(DRIVER_ID).fullName("Updated Name").build();

        DriverUpdateReq req = DriverUpdateReq.builder()
                .fullName("Updated Name")
                .phone("+996777123456")
                .carNumber("01 730 AZX")
                .warehouseId(UUID.randomUUID())
                .status(DriverStatus.ACTIVE)
                .build();

        when(driverService.updateDriver(eq(DRIVER_ID), any())).thenReturn(dto);

        mockMvc.perform(put("/api/drivers/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"));
    }


}