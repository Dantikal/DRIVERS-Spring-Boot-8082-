package com.drivers.modules.events.publisher;

import com.drivers.modules.events.dto.DriverOrderEvent;
import com.drivers.modules.payments.dto.event.PaymentEvent;
import com.drivers.modules.returns.dto.event.ReturnEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DriverEventPublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TOPIC_ORDERS_NEW      = "orders:new";
    private static final String TOPIC_ORDERS_UPDATED  = "orders:updated";
    private static final String TOPIC_PAYMENTS        = "payments:received";
    private static final String TOPIC_RETURNS         = "returns:processed";

    public void publishOrderNew(DriverOrderEvent event) {
        publish(TOPIC_ORDERS_NEW, event, "ORDER event");
    }

    public void publishOrderUpdated(DriverOrderEvent event) {
        publish(TOPIC_ORDERS_UPDATED, event, "ORDER event");
    }

    public void publishPaymentReceived(PaymentEvent event) {
        publish(TOPIC_PAYMENTS, event, "PAYMENT event");
    }

    public void publishReturnProcessed(ReturnEvent event) {
        publish(TOPIC_RETURNS, event, "RETURN event");
    }

    private void publish(String topic, Object event, String label) {
        try {
            redisTemplate.convertAndSend(topic, event);
            log.info("DriverEventPublisher: published {} → topic '{}'", label, topic);
        } catch (Exception e) {
            log.error("DriverEventPublisher: failed to publish {} → topic '{}': {}",
                    label, topic, e.getMessage(), e);
        }
    }
}