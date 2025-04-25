package com.pdf.printer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileOrderItem {
    private String uniqueId; // Frontend temporary ID for reference
    private String fileName; // The actual filename to be printed (B&W or Color)
    private int pageCount;
    private int printType; // 0 for B&W, 1 for Color
    private int numberOfCopies;
    private int calculatedPrice; // Price for this item (recalculated on backend)
    private int printerId;
}