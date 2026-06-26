package com.drivers.confirmation;

import com.drivers.modules.confirmation.controller.ConfirmationController;
import com.drivers.modules.confirmation.service.ConfirmationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConfirmationControllerTest {

    @Mock
    private ConfirmationService confirmationService;

    @InjectMocks
    private ConfirmationController confirmationController;

    @Test
    void confirmationReceipt_ReturnsOk() {
        UUID dispatchId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        ResponseEntity<Void> response = confirmationController.confirmationReceipt(dispatchId, driverId);

        verify(confirmationService).confirmationReceipt(dispatchId, driverId);
        assertEquals(200, response.getStatusCode().value());
    }
}
