package com.drivers.drivers;

import com.drivers.modules.auth.repository.DriverAuthRepository;
import com.drivers.modules.drivers.repository.DriverDebtRepository;
import com.drivers.modules.drivers.repository.DriverRepository;
import com.drivers.modules.drivers.service.impl.DriverServiceImpl;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DriverServiceTest {
    @Mock
    private DriverRepository driverRepository;
    @Mock
    private DriverAuthRepository driverAuthRepository;
    @Mock
    private DriverDebtRepository driverDebtRepository;

    @InjectMocks
    private DriverServiceImpl driverService;




}
