package com.pdf.printer.config;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

@Component
public class PaymentWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebSocketHandler.class);
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WebSocket connection established: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status)
            throws Exception {
        sessions.remove(session);
        log.info("WebSocket connection closed: {} with status {}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // You can handle messages sent by clients here if needed
        log.info("Received message from {}: {}", session.getId(), message.getPayload());
    }@Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage(), exception);
        sessions.remove(session);
    }

    public void broadcastPayment(int amount) {
        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(Map.of("amount", amount));
            TextMessage message = new TextMessage(messageJson);
            for (WebSocketSession session : sessions) {
            	log.info("inside broadcast");
                if (session.isOpen()) {
                	log.info("before send message");
                    session.sendMessage(message);
                    log.info("after send message");
                }
            }
            log.info("Broadcasted payment amount: {}", amount);
        } catch (IOException e) {
            log.error("Error broadcasting payment amount: {}", e.getMessage(), e);
        }
    }
}

