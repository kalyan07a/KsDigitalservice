package com.pdf.printer.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.pdf.printer.service.PaymentDetail;
import com.pdf.printer.service.PaymentService;

@Controller
public class CustomerDashboardController {
    
    private static final Logger log = LoggerFactory.getLogger(CustomerDashboardController.class);
    
    @Autowired
    private PaymentService paymentService;

    @PostMapping("/fetchDetails")
    public ResponseEntity<?> getPaymentDetails(
            @RequestParam String phoneNumber,
            @RequestParam String filterType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        try {
            validateRequest(phoneNumber, filterType, startDate, endDate);
            
            List<PaymentDetail> payments;
            Long totalPayment;
            
            switch (filterType) {
                case "DAY":
                    payments = paymentService.getDailyPayments(phoneNumber);
                    totalPayment = paymentService.getDailyTotal(phoneNumber);
                    break;
                case "CUSTOM":
                    payments = paymentService.getPaymentsByDateRange(phoneNumber, startDate, endDate);
                    totalPayment = paymentService.getTotalByDateRange(phoneNumber, startDate, endDate);
                    break;
                default: // MONTH
                    payments = paymentService.getMonthlyPayments(phoneNumber);
                    totalPayment = paymentService.getMonthlyTotal(phoneNumber);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("payments", payments);
            response.put("totalPayment", totalPayment);
            response.put("name", Printer.getNameByPhone(phoneNumber));
            response.put("phone", phoneNumber);
            response.put("filterType", filterType);
            response.put("startDate", startDate);
            response.put("endDate", endDate);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.error("Server error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving payments: " + e.getMessage());
        }
    }

    private void validateRequest(String phoneNumber, String filterType, 
                                LocalDate startDate, LocalDate endDate) {
        if (!phoneNumber.matches("\\d{10}")) {
            throw new IllegalArgumentException("Invalid phone number format");
        }
        
        if (filterType.equals("CUSTOM")) {
            if (startDate == null || endDate == null) {
                throw new IllegalArgumentException("Both dates are required for custom range");
            }
            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("End date cannot be before start date");
            }
        }
    }
}