package com.pdf.printer.controller;

import com.pdf.printer.dto.FileOrderItem;
import com.pdf.printer.dto.PaymentInitiationRequest;
import com.pdf.printer.service.RazorpayService;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;

import jakarta.mail.MessagingException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;


import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/print")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${spring.mail.username}")
    private String mailUsername;

    @Value("${p-email}")
    private String printerEmail;

    @Value("${razorpay.api.key}")
    private String razorpayApiKey;

    @Value("${razorpay.webhook.secret}")
    private String razorpayWebhookSecret;

    private final RazorpayService razorpayService;
    private final JavaMailSender mailSender;

    @Autowired
    public PaymentController(RazorpayService razorpayService, JavaMailSender mailSender) {
        this.razorpayService = razorpayService;
        this.mailSender = mailSender;
        log.info("PaymentController created and services injected.");
    }

    @PostMapping("/api/payments/initiate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> initiatePayment(@RequestBody PaymentInitiationRequest request) {
        Map<String, Object> response = new HashMap<>();
        log.info("Received payment initiation request for {} items.", request.getItems() != null ? request.getItems().size() : 0);
        log.debug("Payment initiation payload: {}", request);

        if (request.getItems() == null || request.getItems().isEmpty()) {
            log.warn("Payment initiation request received with no items.");
            response.put("error", "No items provided for payment.");
            return ResponseEntity.badRequest().body(response);
        }

        int totalAmountInRupees = 0;
        List<FileOrderItem> validItems = new ArrayList<>();
        JSONArray itemsJsonArray = new JSONArray();

        for (FileOrderItem item : request.getItems()) {
            // Backend validation of item data from request
            if (item.getFileName() == null || item.getFileName().isBlank() || item.getPageCount() <= 0 || item.getNumberOfCopies() <= 0 || (item.getPrintType() != 0 && item.getPrintType() != 1)) {
                log.error("Invalid item data received in payment request: {}", item);
                response.put("error", "Invalid data for item ID: " + item.getUniqueId() + ". File details might be incorrect.");
                return ResponseEntity.badRequest().body(response);
            }

            // Security: Recalculate price on the backend
            int itemPrice = calculateAmount(item.getPageCount(), item.getPrintType(), item.getNumberOfCopies());
            if (itemPrice <= 0 && item.getPageCount() > 0) { // Allow 0 price only if page count is 0
                 log.error("Invalid calculated amount ({}) for item with pages>0: {}", itemPrice, item);
                 response.put("error", "Failed to calculate a valid price for item ID: " + item.getUniqueId());
                 return ResponseEntity.badRequest().body(response);
            }

            item.setCalculatedPrice(itemPrice); // Store backend calculated price
            totalAmountInRupees += itemPrice;
            validItems.add(item);

             JSONObject itemJson = new JSONObject();
             itemJson.put("fName", item.getFileName());
             itemJson.put("pCount", item.getPageCount());
             itemJson.put("pType", item.getPrintType());
             itemJson.put("copies", item.getNumberOfCopies());
             itemsJsonArray.put(itemJson);
        }

        // Allow order creation even if total is 0? Might be valid if only 0-page files.
        // Let's allow it for now, Razorpay might reject if amount is too low.
        if (totalAmountInRupees < 0) { // Only check for negative
             log.error("Total calculated amount is negative ({})! Cannot create order.", totalAmountInRupees);
             response.put("error", "Total calculated amount is invalid.");
             return ResponseEntity.badRequest().body(response);
        }

        log.info("Total calculated amount for {} valid items: Rs. {}", validItems.size(), totalAmountInRupees);

        try {
            String currency = "INR";
            String receiptId = "receipt_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0,4); // More unique receipt

            JSONObject notes = new JSONObject();
            notes.put("item_count", validItems.size());
             String itemsJsonString = itemsJsonArray.toString();
             if (itemsJsonString.length() > 1800) { // Adjust limit based on testing/Razorpay docs
                 log.warn("Items JSON string length ({}) is large and might exceed Razorpay notes limit. Storing truncated or placeholder.", itemsJsonString.length());
                 // Option: Truncate smartly, or store a reference, or just note count.
                 // For now, just add count and maybe first item name
                 notes.put("items_truncated", "Data exceeds notes limit. See receipt " + receiptId);
             } else {
                  notes.put("items", itemsJsonString);
             }

            // Handle 0 amount case - Razorpay requires minimum 1 INR (100 paise)
            if (totalAmountInRupees == 0) {
                log.info("Total amount is 0. Skipping Razorpay order creation. Returning success-like response.");
                // Simulate a successful payment flow without actually charging
                response.put("orderId", "ORDER_SKIPPED_ZERO_AMOUNT_" + receiptId);
                response.put("amount", 0); // 0 paise
                response.put("currency", currency);
                response.put("razorpayKey", razorpayApiKey); // Still needed for consistency? Maybe not.
                response.put("status", "skipped_zero_amount"); // Custom status
                response.put("totalAmountRupees", 0);

                // Directly trigger post-payment actions for zero amount order
                 handlePostPaymentActions(validItems, "SKIPPED_" + receiptId);

                return ResponseEntity.ok(response);
            }


            String orderJsonString = razorpayService.createOrder(totalAmountInRupees, currency, receiptId, notes);
            log.debug("Razorpay Order Created Raw: {}", orderJsonString); // Debug level

            JSONObject orderJson = new JSONObject(orderJsonString);
            String orderId = orderJson.getString("id");
            int orderAmountPaise = orderJson.getInt("amount");

            response.put("orderId", orderId);
            response.put("amount", orderAmountPaise);
            response.put("currency", currency);
            response.put("razorpayKey", razorpayApiKey);
            response.put("status", "created");
            response.put("totalAmountRupees", totalAmountInRupees);

            return ResponseEntity.ok(response);

        } catch (RazorpayException e) {
            log.error("RazorpayException during payment initiation: {}", e.getMessage(), e);
            response.put("error", "Payment Error: " + (e.getMessage() != null ? e.getMessage() : "Gateway error"));
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
            log.error("Unexpected error during payment initiation processing", e);
            response.put("error", "An unexpected server error occurred.");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/api/payments/webhook")
    public ResponseEntity<String> handleRazorpayWebhook(@RequestBody String payload, @RequestHeader("X-Razorpay-Signature") String signature) {
        log.info("Received Razorpay Webhook Request");
        log.debug("Webhook Payload: {}", payload);
        log.debug("Webhook Signature: {}", signature);

        if (razorpayWebhookSecret == null || razorpayWebhookSecret.isBlank()) {
             log.error("Razorpay Webhook Secret is not configured on the server.");
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook secret not configured");
        }

        try {
            boolean isValid = Utils.verifyWebhookSignature(payload, signature, razorpayWebhookSecret);
            if (!isValid) {
                log.warn("Invalid Razorpay webhook signature received.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
            }
            log.info("Webhook signature verified successfully.");

            JSONObject payloadJson = new JSONObject(payload);
            String event = payloadJson.optString("event");
            log.info("Webhook event type: {}", event);

            if ("payment.captured".equals(event)) {
                JSONObject paymentPayload = payloadJson.optJSONObject("payload");
                if (paymentPayload == null) { log.error("Webhook Error: Missing 'payload' object."); return ResponseEntity.badRequest().body("Missing payload"); }
                JSONObject payment = paymentPayload.optJSONObject("payment");
                if (payment == null) { log.error("Webhook Error: Missing 'payment' object in payload."); return ResponseEntity.badRequest().body("Missing payment payload"); }
                 JSONObject entity = payment.optJSONObject("entity");
                if (entity == null) { log.error("Webhook Error: Missing 'entity' object in payment payload."); return ResponseEntity.badRequest().body("Missing payment entity"); }


                String orderId = entity.optString("order_id");
                String paymentId = entity.optString("id");
                JSONObject notes = entity.optJSONObject("notes");

                log.info("Processing 'payment.captured' for Order ID: {}, Payment ID: {}", orderId, paymentId);

                // Check if notes exist and contain items or the truncated flag
                if (notes == null || (!notes.has("items") && !notes.has("items_truncated"))) {
                    log.error("Webhook Error: Missing 'notes' or expected keys ('items'/'items_truncated') in notes for order_id: {}", orderId);
                     // Critical data missing, cannot process reliably.
                     // Acknowledge receipt but indicate failure to process items.
                     return ResponseEntity.ok("Webhook processed (acknowledged), but required item data missing in notes.");
                }

                List<FileOrderItem> itemsToProcess = new ArrayList<>();
                // Try parsing 'items' string first
                if (notes.has("items")) {
                     try {
                        String itemsJsonString = notes.getString("items");
                        JSONArray itemsJsonArray = new JSONArray(itemsJsonString);
                        for (int i = 0; i < itemsJsonArray.length(); i++) {
                            JSONObject itemJson = itemsJsonArray.getJSONObject(i);
                            FileOrderItem item = new FileOrderItem();
                            item.setFileName(itemJson.optString("fName", null));
                            item.setPageCount(itemJson.optInt("pCount", -1));
                            item.setPrintType(itemJson.optInt("pType", -1));
                            item.setNumberOfCopies(itemJson.optInt("copies", -1));

                            if (item.getFileName() == null || item.getPageCount() == -1 || item.getPrintType() == -1 || item.getNumberOfCopies() == -1) {
                                log.error("Webhook Error: Incomplete item data parsed from notes for order_id: {}, item index: {}, data: {}", orderId, i, itemJson);
                                continue;
                            }
                            itemsToProcess.add(item);
                        }
                        log.info("Successfully parsed {} items from 'items' note for order ID: {}", itemsToProcess.size(), orderId);
                     } catch (Exception e) {
                         log.error("Webhook Error: Failed to parse 'items' JSON string from notes for order_id: {}. Attempting fallback if possible.", orderId, e);
                         // If parsing fails, maybe try fallback if notes.has("items_truncated")? Or just fail here.
                         return ResponseEntity.badRequest().body("Error parsing payment notes (items)");
                     }
                } else if (notes.has("items_truncated")) {
                     // Item data was too large for notes. Need alternative mechanism.
                     log.error("Webhook Error: Item data was truncated in notes for order_id: {}. Cannot process automatically. Manual intervention required.", orderId);
                     // You MUST have another way to retrieve order details here, e.g., querying your DB using orderId or receiptId.
                     // Since that's not implemented here, we have to acknowledge and report the issue.
                     return ResponseEntity.ok("Webhook processed (acknowledged), but item data was truncated. Manual processing needed.");
                }


                if (itemsToProcess.isEmpty()) {
                     log.warn("Webhook Warning: No valid items found to process after parsing notes for order_id: {}", orderId);
                     return ResponseEntity.ok("Webhook processed, but no valid items found in notes.");
                }

                // Perform Post-Payment Actions
                handlePostPaymentActions(itemsToProcess, orderId);

                return ResponseEntity.ok("Webhook processed successfully.");

            } else {
                log.info("Ignoring non-'payment.captured' webhook event: {}", event);
                return ResponseEntity.ok("Event received but not processed: " + event);
            }

        } catch (RazorpayException e) {
            log.error("RazorpayException during webhook signature verification: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook signature verification failed");
        } catch (Exception e) {
            log.error("Unexpected error processing webhook payload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error processing webhook");
        }
    }

   // Extracted post-payment logic to a separate method
    private void handlePostPaymentActions(List<FileOrderItem> itemsToProcess, String orderReference) {
         int successCount = 0;
         int failureCount = 0;
         log.info("Starting post-payment actions for {} items. Reference: {}", itemsToProcess.size(), orderReference);

         for (FileOrderItem item : itemsToProcess) {
             try {
                 log.info("Processing item: File='{}', Type={}, Copies={}, Pages={}",
                          item.getFileName(), item.getPrintType(), item.getNumberOfCopies(), item.getPageCount());

                 // Send Email
                 sendEmailWithAttachment(item.getFileName(), item.getPrintType(), item.getNumberOfCopies(), item.getPageCount());

                 // Delete File
                 deleteFile(item.getFileName());

                 successCount++;
                 log.info("Successfully processed post-payment actions for file: {}", item.getFileName());

             } catch (MessagingException | IOException e) {
                 failureCount++;
                 log.error("Post-Payment Action Error: Failed (email/delete) for file: {} (Reference: {}). Error: {}",
                           item.getFileName(), orderReference, e.getMessage());
                 // Consider queuing for retry or notifying admin
             }
         } // End loop

         log.info("Post-payment actions complete for Reference: {}. Success: {}, Failures: {}",
                  orderReference, successCount, failureCount);
    }


    private int calculateAmount(int pages, int printType, int numberOfCopies) {
        if (pages < 0 || numberOfCopies < 1 || (printType != 0 && printType != 1)) { // Allow pages = 0
            log.warn("Invalid input for amount calculation: pages={}, printType={}, copies={}", pages, printType, numberOfCopies);
            return -1; // Indicate error
        }
        if (pages == 0) {
             return 0; // 0 pages cost 0
        }

        int firstCopyPrice;
        if (printType == 0) {
            firstCopyPrice = (pages <= 2) ? pages * 10 : (pages - 2) * 2 + 20;
        } else {
            firstCopyPrice = (pages <= 1) ? pages * 20 : (pages - 1) * 5 + 20;
        }

        if (numberOfCopies == 1) {
            return firstCopyPrice;
        }

        int additionalCopyCost;
        if (printType == 0) {
            additionalCopyCost = pages * 2;
        } else {
            additionalCopyCost = pages * 5;
        }

        int totalAmount = firstCopyPrice + (additionalCopyCost * (numberOfCopies - 1));
        return totalAmount;
    }

    private void sendEmailWithAttachment(String fileName, int printType, int copies, int pages) throws MessagingException {
        Path filePath = Paths.get(uploadDir).resolve(fileName); // Resolve against upload dir
        log.info("Preparing email for: File='{}', Path='{}', Type={}, Copies={}, Pages={}",
                 fileName, filePath, printType, copies, pages);

        if (!Files.exists(filePath)) {
            log.error("File not found for email attachment: {}", filePath);
            throw new MessagingException("Attachment file not found: " + fileName);
        }

         if (mailUsername == null || mailUsername.isBlank() || printerEmail == null || printerEmail.isBlank()) {
             log.error("Email 'from' address or 'to' address not configured. Cannot send email.");
             throw new MessagingException("Email configuration missing (sender or recipient).");
         }

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8"); // Multipart

            helper.setFrom(mailUsername);
            helper.setTo(printerEmail);

            String typeStr = (printType == 0) ? "B&W" : "Color";
            String subject = String.format("Print Request: %s - %d Pages - %d Copies - %s",
                                           typeStr, pages, copies, fileName);
            helper.setSubject(subject);

            String emailBody = String.format(
                "Print Request Details:\n\n" +
                "File: %s\n" +
                "Type: %s\n" +
                "Pages: %d\n" +
                "Copies: %d\n\n" +
                "Please find the file attached.",
                fileName, typeStr, pages, copies
            );
            helper.setText(emailBody);

            helper.addAttachment(fileName, filePath.toFile());

            log.info("Sending email to {} with subject: {}", printerEmail, subject);
            mailSender.send(message);
            log.info("Email sent successfully for file: {}", fileName);

        } catch (MessagingException e) {
            log.error("Failed to prepare or send email for file: {}", fileName, e);
            throw e;
        } catch (Exception e) {
             log.error("Unexpected error during email sending for file: {}", fileName, e);
             throw new MessagingException("Unexpected error sending email: " + e.getMessage(), e);
        }
    }

    private void deleteFile(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            log.warn("Attempted to delete file with null or empty name. Skipping.");
            return;
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            log.error("Potential path traversal detected in filename for deletion: '{}'. Skipping deletion.", fileName);
            throw new IOException("Invalid filename for deletion: " + fileName);
        }

        Path path = Paths.get(uploadDir).resolve(fileName).normalize();

         if (!path.startsWith(Paths.get(uploadDir).normalize())) {
             log.error("Resolved path '{}' is outside the upload directory '{}'. Deletion aborted.", path, uploadDir);
             throw new IOException("Attempt to delete file outside designated directory.");
         }

        try {
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("File deleted successfully: {}", path);
            } else {
                log.warn("File not found for deletion, skipping: {}", path);
            }
        } catch (IOException e) {
            log.error("Failed to delete file: {}", path, e);
            throw e;
        }
    }

    // --- Static Page Mappings (if needed) ---
     @GetMapping("/terms") public String terms() { return "terms"; }
     @GetMapping("/privacy") public String privacy() { return "privacy"; }
     @GetMapping("/cancellation") public String cancellation() { return "cancellation"; }
     @GetMapping("/contact") public String contact() { return "contact"; }
     @GetMapping("/google0062e0de736cb797.html") public String owner() { return "google0062e0de736cb797"; }

}