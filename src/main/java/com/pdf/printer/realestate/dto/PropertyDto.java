
package com.pdf.printer.realestate.dto;

import java.util.List;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class PropertyDto {
    private String productId;
    private String title;
    private String location;
    private Double carpetArea;
    private Double amount;
    private String additionalInfo;
    private List<MultipartFile> images;      // multiple images
}
