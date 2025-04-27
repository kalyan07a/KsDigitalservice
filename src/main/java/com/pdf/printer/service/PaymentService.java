package com.pdf.printer.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pdf.printer.repo.PaymentDetailRepository;
import org.springframework.transaction.annotation.Transactional; // Import Transactional

@Service
public class PaymentService {
	private static final Logger log=LoggerFactory.getLogger(PaymentService.class);
	
    
    private final PaymentDetailRepository paymentDetailRepository;

    @Autowired
    public PaymentService(PaymentDetailRepository paymentDetailRepository) {
        this.paymentDetailRepository = paymentDetailRepository;
    }

    // Existing methods...

    public List<PaymentDetail> getMonthlyPayments(String phoneNumber) {
        return paymentDetailRepository.findByPhoneNumberCurrentMonth(phoneNumber);
    }

    public Long getMonthlyTotal(String phoneNumber) {
        return paymentDetailRepository.findMonthlyTotal(phoneNumber);
    }

    public List<PaymentDetail> getDailyPayments(String phoneNumber) {
        return paymentDetailRepository.findTodayPayments(phoneNumber);
    }

    public Long getDailyTotal(String phoneNumber) {
        return paymentDetailRepository.findTodayTotal(phoneNumber);
    }

    public List<PaymentDetail> getPaymentsByDateRange(String phoneNumber, LocalDate start, LocalDate end) {
        return paymentDetailRepository.findByDateRange(phoneNumber, start, end);
    }

    public Long getTotalByDateRange(String phoneNumber, LocalDate start, LocalDate end) {
        return paymentDetailRepository.findTotalByDateRange(phoneNumber, start, end);
    }

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
    public Optional<PaymentDetail> findByPaymentId(String paymentId) {
        return paymentDetailRepository.findByPaymentId(paymentId);
    }
}