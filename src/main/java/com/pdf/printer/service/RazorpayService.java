package com.pdf.printer.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct; // Use jakarta version

@Service
public class RazorpayService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayService.class);

    @Value("${razorpay.api.key}")
    private String keyId;

    @Value("${razorpay.api.secret}") // **Ensure this is injected**
    private String keySecret;

    private RazorpayClient razorpayClient;

    // Initialize client after properties are injected
    @PostConstruct
    public void init() {
        try {
            this.razorpayClient = new RazorpayClient(this.keyId, this.keySecret);
            log.info("RazorpayClient Initialized successfully with Key ID: {}", this.keyId);
        } catch (RazorpayException e) {
            log.error("!!! Failed to initialize RazorpayClient: {} !!!", e.getMessage(), e);
            // Depending on your application's needs, you might want to prevent startup
            // throw new RuntimeException("Failed to initialize Razorpay client", e);
        }
    }

    public String createOrder(int amountInRupees, String currency, String receiptId, JSONObject notes) throws RazorpayException {
        if (this.razorpayClient == null) {
            log.error("RazorpayClient is not initialized. Cannot create order.");
            throw new RazorpayException("Razorpay client not available. Check initialization.");
        }

        try {
            JSONObject orderRequest = new JSONObject();
            // Amount should be in the smallest currency unit (paise for INR)
            orderRequest.put("amount", amountInRupees * 100);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", receiptId);
            if (notes != null) {
                orderRequest.put("notes", notes);
            }

            log.info("Creating Razorpay order with request: {}", orderRequest.toString());
            Order order = this.razorpayClient.orders.create(orderRequest);
            log.info("Razorpay order response: {}", order.toString());
            return order.toString();

        } catch (RazorpayException e) {
            log.error("RazorpayException during order creation: {}", e.getMessage(), e);
            throw e; // Re-throw the exception to be handled by the controller
        }
    }
}