package com.pdf.printer.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
class PaymentRequest {
    public String paymentId;
    public String date; // Receive as String initially for flexibility
    public String time; // Receive as String initially
    public BigDecimal amount;
}
