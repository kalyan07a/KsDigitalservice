/*
 * package com.pdf.printer.controller;
 * 
 * 
 * import org.json.JSONObject; import
 * org.springframework.beans.factory.annotation.Autowired; import
 * org.springframework.web.bind.annotation.PostMapping; import
 * org.springframework.web.bind.annotation.RequestMapping; import
 * org.springframework.web.bind.annotation.RequestParam; import
 * org.springframework.web.bind.annotation.RestController; import
 * com.pdf.printer.service.RazorpayService; import
 * com.razorpay.RazorpayException;
 * 
 * @RestController
 * 
 * @RequestMapping("/api/payments") public class PaymentController {
 * 
 * @Autowired private RazorpayService razorpayService;
 * 
 * @PostMapping("/create-order") public String createOrder(@RequestParam int
 * amount , @RequestParam String currency){
 * 
 * try { System.out.println("payment page"); String
 * order=razorpayService.createOrder(amount, currency, "recepient_100");
 * System.out.println("order details"+order.toString());
 * System.out.println(extractStatusFromString(order)); return order; } catch
 * (RazorpayException e) { throw new RuntimeException(e); } } public static
 * String extractStatusFromString(String orderString) { int jsonStartIndex =
 * orderString.indexOf("{"); if (jsonStartIndex == -1) { throw new
 * IllegalArgumentException("Invalid order string format: JSON part not found."
 * ); } String jsonString = orderString.substring(jsonStartIndex); JSONObject
 * jsonObject = new JSONObject(jsonString); return
 * jsonObject.getString("status"); } }
 */