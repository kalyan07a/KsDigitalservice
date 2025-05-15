package com.pdf.printer.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.pdf.printer.service.PaymentDetail;

@Repository
public interface PaymentDetailRepository extends JpaRepository<PaymentDetail, Long> {
    Optional<PaymentDetail> findByPaymentId(String paymentId);

    // Current Month
    @Query("SELECT p FROM PaymentDetail p WHERE MONTH(p.paymentDate) = MONTH(CURRENT_DATE) AND YEAR(p.paymentDate) = YEAR(CURRENT_DATE) AND p.phoneNumber = ?1  ORDER BY p.paymentDate DESC")
    List<PaymentDetail> findByPhoneNumberCurrentMonth(String phoneNumber);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentDetail p WHERE MONTH(p.paymentDate) = MONTH(CURRENT_DATE)  AND YEAR(p.paymentDate) = YEAR(CURRENT_DATE)  AND p.phoneNumber = ?1")
    Long findMonthlyTotal(String phoneNumber);

    // Today
    @Query("SELECT p FROM PaymentDetail p WHERE p.paymentDate = CURRENT_DATE AND p.phoneNumber = ?1 ORDER BY p.paymentDate DESC")
    List<PaymentDetail> findTodayPayments(String phoneNumber);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentDetail p WHERE p.paymentDate = CURRENT_DATE  AND p.phoneNumber = ?1")         
    Long findTodayTotal(String phoneNumber);

    // Custom Date Range
    @Query("SELECT p FROM PaymentDetail p WHERE p.phoneNumber = ?1 AND p.paymentDate BETWEEN ?2 AND ?3 ORDER BY p.paymentDate DESC")     
    List<PaymentDetail> findByDateRange(String phoneNumber, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentDetail p  WHERE p.phoneNumber = ?1 AND p.paymentDate BETWEEN ?2 AND ?3")
    Long findTotalByDateRange(String phoneNumber, LocalDate startDate, LocalDate endDate);
}