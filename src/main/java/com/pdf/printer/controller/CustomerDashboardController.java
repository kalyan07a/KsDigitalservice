package com.pdf.printer.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.pdf.printer.service.PaymentDetail;
import com.pdf.printer.service.PaymentService;

@Controller
public class CustomerDashboardController {
	 private static final Logger log = LoggerFactory.getLogger(CustomerDashboardController.class);
	
	@GetMapping("/myDashboard")
	public String dashboard() {
		return "customerDashboard";
	}
	
	 @Autowired
	    private PaymentService paymentService;

		/*
		 * @PostMapping("/fetchDetails") public ResponseEntity<?>
		 * getPaymentDetails(@RequestParam String phoneNumber) { try {
		 * List<PaymentDetail> payments =
		 * paymentService.getPaymentsByPhoneNumber(phoneNumber); Long totalPayment =
		 * paymentService.getTotalPaymentsByPhoneNumber(phoneNumber); return
		 * ResponseEntity.ok(payments); } catch (Exception e) { return
		 * ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
		 * .body("Error retrieving payments: " + e.getMessage()); } }
		 */
	 
	 @PostMapping("/fetchDetails")
	 public ResponseEntity<?> getPaymentDetails(@RequestParam String phoneNumber) {
	     try {
	         List<PaymentDetail> payments = paymentService.getPaymentsByPhoneNumber(phoneNumber);
	         Long totalPayment = paymentService.getTotalPaymentsByPhoneNumber(phoneNumber);
	         
	         String name = Printer.getNameByPhone(phoneNumber);
	         Map<String, Object> response = new HashMap<>();
	         response.put("payments", payments);
	         response.put("totalPayment", totalPayment);
	         response.put("name", name);
	         response.put("phone", phoneNumber);
	         log.info("response is "+response);
	         
	         return ResponseEntity.ok(response);
	     } catch (Exception e) {
	         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                 .body("Error retrieving payments: " + e.getMessage());
	     }
	 }
	 
	}