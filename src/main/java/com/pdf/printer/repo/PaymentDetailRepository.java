package com.pdf.printer.repo;


import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.pdf.printer.service.PaymentDetail;

@Repository 
public interface PaymentDetailRepository extends JpaRepository<PaymentDetail, Long> {

    Optional<PaymentDetail> findByPaymentId(String paymentId);
    
    @Query("SELECT p FROM PaymentDetail p WHERE MONTH(p.paymentDate) = MONTH(CURRENT_DATE) AND YEAR(p.paymentDate) = YEAR(CURRENT_DATE) AND p.phoneNumber = ?1")
    List<PaymentDetail> findByPhoneNumber(String phoneNumber);
    
    
    @Query("SELECT SUM(p.amount) FROM PaymentDetail p WHERE MONTH(p.paymentDate) = MONTH(CURRENT_DATE) AND YEAR(p.paymentDate) = YEAR(CURRENT_DATE) AND p.phoneNumber = ?1")
    Long findTotalAmountByPhoneNumber(String phoneNumber);

}