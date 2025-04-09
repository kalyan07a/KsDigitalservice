package com.pdf.printer.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Service
public class RazorpayService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayService.class);

    @Value("${razorpay.api.key}")
    private String keyId;

    @Value("${razorpay.api.secret}")
    private String keySecret;

    private RazorpayClient razorpayClient;

    @PostConstruct
    public void init() {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            log.error("!!! Razorpay API Key ID or Key Secret is missing in configuration. Razorpay client WILL NOT be initialized. !!!");
            return; // Prevent initialization if keys are missing
        }
        try {
            this.razorpayClient = new RazorpayClient(this.keyId, this.keySecret);
            log.info("RazorpayClient Initialized successfully with Key ID ending in: ...{}", keyId.length() > 4 ? keyId.substring(keyId.length() - 4) : keyId);
        } catch (RazorpayException e) {
            log.error("!!! Failed to initialize RazorpayClient: {} !!! Check Key ID and Key Secret.", e.getMessage(), e);
            // throw new RuntimeException("Failed to initialize Razorpay client", e); // Consider halting startup
        }
    }

    public String createOrder(int totalAmountInRupees, String currency, String receiptId, JSONObject notes) throws RazorpayException {
        if (this.razorpayClient == null) {
            log.error("RazorpayClient is not initialized. Cannot create order. Check API keys in configuration.");
            throw new RazorpayException("Razorpay client not available. Check server configuration and logs.");
        }

        if (totalAmountInRupees <= 0) {
            log.error("Attempted to create Razorpay order with invalid amount: {}", totalAmountInRupees);
            throw new IllegalArgumentException("Order amount must be positive.");
        }

        try {
            JSONObject orderRequest = new JSONObject();
            int amountInPaise = totalAmountInRupees * 100;
             if (amountInPaise < 100) { // Razorpay minimum is typically 1 INR
                 log.warn("Calculated amount in paise ({}) is less than 100 (1 INR). Razorpay might reject this.", amountInPaise);
             }
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", receiptId);
            if (notes != null && notes.length() > 0) {
                orderRequest.put("notes", notes);
            } else {
                log.warn("Creating Razorpay order without notes for receipt: {}", receiptId);
            }

            log.info("Creating Razorpay order. Request: Amount={} paise, Currency={}, Receipt={}, Notes keys={}",
                     amountInPaise, currency, receiptId, notes != null ? notes.keySet() : "none");

            Order order = this.razorpayClient.orders.create(orderRequest);

            log.info("Razorpay order created successfully. Order ID: {}, Amount: {}, Receipt: {}",
                     order.get("id"), order.get("amount"), order.get("receipt"));
            log.debug("Full Razorpay order response: {}", order);
            return order.toString();

        } catch (RazorpayException e) {
            log.error("RazorpayException during order creation (Receipt: {}): Status Code: {}, Message: {}",
                      receiptId, e.getMessage(), e);
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("authentication failed")) {
                log.error("!!! Razorpay Authentication Failed! Verify Key ID and Key Secret. !!!");
                throw new RazorpayException("Authentication failed with Razorpay. Please contact support.", e);
            }
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during Razorpay order creation process for receipt: {}", receiptId, e);
            throw new RuntimeException("Unexpected error preparing Razorpay order.", e);
        }
    }
}