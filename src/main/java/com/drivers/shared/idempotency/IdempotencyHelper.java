package com.drivers.shared.idempotency;

import com.drivers.modules.orders.entity.DriverOrder;
import com.drivers.modules.orders.repository.DriverOrderRepo;
import com.drivers.modules.payments.entity.DriverPayment;
import com.drivers.modules.payments.repository.DriverPaymentRepo;
import com.drivers.modules.returns.entity.ReturnRequest;
import com.drivers.modules.returns.repository.ReturnRequestRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helper component that saves entities in a NEW, isolated transaction so that
 * a DataIntegrityViolationException (concurrent idempotency key collision) does
 * NOT mark the caller's transaction as rollback-only.
 * <p>
 * If the flush violates the unique constraint the inner transaction is rolled
 * back cleanly and the caller can safely catch the exception and return the
 * already-persisted entity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyHelper {

    private final DriverOrderRepo orderRepo;
    private final DriverPaymentRepo paymentRepo;
    private final ReturnRequestRepo returnRequestRepo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DriverOrder saveOrder(DriverOrder order) {
        return orderRepo.saveAndFlush(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DriverPayment savePayment(DriverPayment payment) {
        return paymentRepo.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReturnRequest saveReturn(ReturnRequest returnRequest) {
        return returnRequestRepo.saveAndFlush(returnRequest);
    }
}
