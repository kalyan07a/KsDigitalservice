package com.pdf.printer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PaymentWebSocketHandler paymentWebSocketHandler;

    public WebSocketConfig(PaymentWebSocketHandler paymentWebSocketHandler) {
        this.paymentWebSocketHandler = paymentWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Define a dedicated WebSocket endpoint here. It MUST be different from the webhook URL
        registry.addHandler(paymentWebSocketHandler, "/ws/payment-updates").setAllowedOrigins("*");
    }
}