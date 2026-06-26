package com.drivers.modules.confirmation.controller;

import com.drivers.modules.confirmation.service.ConfirmationService;
import com.drivers.shared.util.CurrentDriverId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/drivers/dispatch")
@RequiredArgsConstructor
public class ConfirmationController {

    private final ConfirmationService confirmationService;

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Void> confirmationReceipt(
            @PathVariable("id") UUID id,
            @CurrentDriverId UUID driverId) {

        confirmationService.confirmationReceipt(id, driverId);
        return ResponseEntity.ok().build();
    }
}