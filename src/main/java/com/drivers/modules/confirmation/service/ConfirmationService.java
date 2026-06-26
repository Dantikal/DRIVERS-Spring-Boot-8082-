package com.drivers.modules.confirmation.service;

import java.util.UUID;

public interface ConfirmationService {
    void confirmationReceipt(UUID dispatchId, UUID driverId);
}
