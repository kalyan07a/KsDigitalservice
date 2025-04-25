package com.pdf.printer.service;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Import Transactional

import com.pdf.printer.controller.CustomerDashboardController;
import com.pdf.printer.repo.PaymentDetailRepository;

@Service 
public class PaymentService {
	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentDetailRepository paymentDetailRepository;

    @Autowired
    public PaymentService(PaymentDetailRepository paymentDetailRepository) {
        this.paymentDetailRepository = paymentDetailRepository;
    }

    /**
     * Creates and saves a new payment detail record.
     *
     * @param paymentId Unique ID for the payment
     * @param date Payment date
     * @param time Payment time
     * @param blackPages Number of black pages
     * @param colorPages Number of color pages
     * @param amount Payment amount
     * @return The saved PaymentDetail entity
     * @throws IllegalArgumentException if a payment with the same paymentId already exists
     */
    @Transactional 
    public PaymentDetail recordPayment(String paymentId, LocalDate date, LocalTime time,
                                        BigDecimal amount, String phone) {

       
        Optional<PaymentDetail> existingPayment = paymentDetailRepository.findByPaymentId(paymentId);
        if (existingPayment.isPresent()) {
           
            throw new IllegalArgumentException("Payment with ID '" + paymentId + "' already exists.");
        }

        // Create a new PaymentDetail entity instance
        PaymentDetail newPayment = new PaymentDetail();
        newPayment.setPaymentId(paymentId);
        newPayment.setPaymentDate(date);
        newPayment.setPaymentTime(time);
        newPayment.setAmount(amount);
        newPayment.setPhoneNumber(phone);

        // The @PrePersist callback in PaymentDetail will set createdAt/updatedAt

        // Save the entity using the repository
        PaymentDetail savedPayment = paymentDetailRepository.save(newPayment);

        // Log or perform other actions if needed
        log.info("Saved Payment Detail: " + savedPayment.getId() + " | Payment ID: " + savedPayment.getPaymentId());
        log.info("savedPayment details are "+savedPayment);
        return savedPayment;
    }

    // --- Other potential service methods ---

    public Optional<PaymentDetail> findByPaymentId(String paymentId) {
        return paymentDetailRepository.findByPaymentId(paymentId);
    }

    public List<PaymentDetail> getPaymentsByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() != 10) {
            throw new IllegalArgumentException("Invalid phone number");
        }
        return paymentDetailRepository.findByPhoneNumber(phoneNumber);
    }

	public Long getTotalPaymentsByPhoneNumber(String phoneNumber) {
		if (phoneNumber == null || phoneNumber.length() != 10) {
            throw new IllegalArgumentException("Invalid phone number");
        }
		return paymentDetailRepository.findTotalAmountByPhoneNumber(phoneNumber);
	}

    // Add methods for finding, updating, deleting payments as needed
}