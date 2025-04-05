package com.pdf.printer.dto;

// Using Lombok for boilerplate code, ensure Lombok dependency is in pom.xml
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data // Generates getters, setters, toString, equals, hashCode
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiationRequest {
    private String fileName;
    private int pageCount;
    private int printType; // 0 for B&W, 1 for Color
}