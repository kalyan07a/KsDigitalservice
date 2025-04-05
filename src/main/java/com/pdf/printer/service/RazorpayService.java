package com.pdf.printer.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RazorpayService {

    @Value("${razorpay.api.key}")
    private String apiKey;

    @Value("${razorpay.api.secret}")
    private String apiSecret;

    // Modified to accept notes
    public String createOrder(int amount, String currency, String receiptId, JSONObject notes) throws RazorpayException {
        RazorpayClient razorpayClient = new RazorpayClient(apiKey, apiSecret);
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amount * 100); // Amount should be in paise
        orderRequest.put("currency", currency);
        orderRequest.put("receipt", receiptId);
        if (notes != null) {
            orderRequest.put("notes", notes); // Add notes to the order request
        }

        Order order = razorpayClient.orders.create(orderRequest);

        // Return the order details as a JSON string
        return order.toString();
    }
}