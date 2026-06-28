package com.drivers.websocket;

import com.drivers.modules.events.dto.DriverOrderEvent;
import com.drivers.modules.events.publisher.DriverEventPublisher;
import com.drivers.modules.orders.entity.OrderStatus;
import com.drivers.shared.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private DriverEventPublisher driverEventPublisher;

    private WebSocketStompClient stompClient;
    private String validJwtToken;

    @BeforeEach
    public void setup() {
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);

        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        UserDetails userDetails = User.withUsername("77771234567")
                .password("test")
                .authorities("ROLE_DRIVER")
                .build();
                
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        validJwtToken = jwtUtil.generateToken(auth, UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    public void testWebSocketOrderNewTopic() throws Exception {
        CompletableFuture<DriverOrderEvent> completableFuture = new CompletableFuture<>();

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + validJwtToken);

        String url = "ws://localhost:" + port + "/ws";

        StompSession stompSession = stompClient.connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
        }).get(5, TimeUnit.SECONDS);

        stompSession.subscribe("/topic/orders/new", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DriverOrderEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                completableFuture.complete((DriverOrderEvent) payload);
            }
        });
        
        // Wait for subscription to be established
        Thread.sleep(1000);

        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        DriverOrderEvent event = DriverOrderEvent.builder()
                .orderId(orderId)
                .driverId(driverId)
                .warehouseId(UUID.randomUUID())
                .status(OrderStatus.NEW)
                .totalAmount(BigDecimal.TEN)
                .eventType("NEW")
                .timestamp("2023-01-01")
                .build();
        driverEventPublisher.publishOrderNew(event);

        DriverOrderEvent receivedEvent = completableFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(receivedEvent);
        assertEquals(orderId, receivedEvent.orderId());
        assertEquals("NEW", receivedEvent.eventType());
    }

    @Test
    public void testWebSocketOrderUpdatedTopic() throws Exception {
        CompletableFuture<DriverOrderEvent> completableFuture = new CompletableFuture<>();

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + validJwtToken);

        String url = "ws://localhost:" + port + "/ws";

        StompSession stompSession = stompClient.connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
        }).get(5, TimeUnit.SECONDS);

        stompSession.subscribe("/topic/orders/updated", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DriverOrderEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                completableFuture.complete((DriverOrderEvent) payload);
            }
        });
        
        // Wait for subscription to be established
        Thread.sleep(1000);

        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        DriverOrderEvent event = DriverOrderEvent.builder()
                .orderId(orderId)
                .driverId(driverId)
                .warehouseId(UUID.randomUUID())
                .status(OrderStatus.DISPATCHED)
                .totalAmount(BigDecimal.TEN)
                .eventType("UPDATED")
                .timestamp("2023-01-01")
                .build();
        driverEventPublisher.publishOrderUpdated(event);

        DriverOrderEvent receivedEvent = completableFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(receivedEvent);
        assertEquals(orderId, receivedEvent.orderId());
        assertEquals("UPDATED", receivedEvent.eventType());
    }
}
