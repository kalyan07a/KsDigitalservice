
package com.pdf.printer.realestate.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
 
@Entity
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String productId;
    private String title;
    private String location;
    private Double carpetArea;
    private Double amount;

    @Column(length = 3000)
    private String additionalInfo;
 
    /** Comma-separated list of stored image filenames */
    private String imageNames;

    @Enumerated(EnumType.STRING)
    private SaleStatus status;
    
    // Helper methods for images
    @Transient
    public List<String> getImageList() {
        if (imageNames == null || imageNames.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(imageNames.split(","))
                    .stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
    }
    
    @Transient
    public String getFirstImage() {
        List<String> images = getImageList();
        return images.isEmpty() ? null : images.get(0);
    }
    
    @Transient
    public int getImageCount() {
        return getImageList().size();
    }
    
    @Transient
    public boolean hasImages() {
        return !getImageList().isEmpty();
    }
}
