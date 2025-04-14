package com.pdf.printer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.pdf.printer.config.PaymentWebSocketHandler;

@Configuration
@EnableWebSocket // Add WebSocket support
public class WebConfig implements WebMvcConfigurer, WebSocketConfigurer { // Implement both interfaces

    @Autowired
    private PaymentWebSocketHandler paymentWebSocketHandler;

    @Value("${file.upload-dir}")
    private String uploadDir;

    // Existing resource handler configuration
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }

    // New WebSocket configuration
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(paymentWebSocketHandler, "/payment-websocket")
                .setAllowedOrigins("*"); // Allow all origins (adjust for production)
    }
}