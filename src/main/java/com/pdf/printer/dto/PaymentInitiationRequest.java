package com.pdf.printer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class PaymentInitiationRequest {
    private List<FileOrderItem> items;
}