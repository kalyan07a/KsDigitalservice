package com.pdf.printer.service;


import jakarta.persistence.*; 
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime; 

@Table(name = "payment_details") 
@Data
@NoArgsConstructor 
@AllArgsConstructor
@Entity
public class PaymentDetail {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Configures auto-increment for the ID
    private Long id;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate; 

    @Column(name = "payment_time", nullable = false)
    private LocalTime paymentTime;

    @Column(name = "payment_id", nullable = false, unique = true, length = 255)
    private String paymentId;
    
    @Column(name = "phone_number", nullable = false, length = 10)
    private String phoneNumber;


    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount; 

    @Column(name = "created_at", updatable = false)
    @Temporal(TemporalType.TIMESTAMP) // Needed if using java.util.Date
    private LocalDateTime createdAt; // Or java.util.Date

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP) // Needed if using java.util.Date
    private LocalDateTime updatedAt; // Or java.util.Date

    // --- Lifecycle Callbacks for Timestamps (Optional) ---
    @PrePersist // Runs before the entity is first saved
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        // Set defaults if not already set (alternative to setting in field declaration)
    }

    @PreUpdate // Runs before an existing entity is updated
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}