package com.pdf.printer.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.pdf.printer.dto.FileOrderItem;
import com.pdf.printer.dto.PaymentInitiationRequest;
import com.pdf.printer.service.RazorpayService;
import com.pdf.printer.config.PaymentWebSocketHandler; // Import the WebSocket handler
import com.razorpay.RazorpayException;
import com.razorpay.Utils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

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
    private final PaymentWebSocketHandler paymentWebSocketHandler; // Inject the WebSocket handler
    int receivedAmount = 0;

    @Autowired
    public PaymentController(RazorpayService razorpayService, JavaMailSender mailSender,
            PaymentWebSocketHandler paymentWebSocketHandler) {
        this.razorpayService = razorpayService;
        this.mailSender = mailSender;
        this.paymentWebSocketHandler = paymentWebSocketHandler;
        log.info("PaymentController created and services injected.");
    }

    @PostMapping("/api/payments/initiate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> initiatePayment(@RequestBody PaymentInitiationRequest request) {
        Map<String, Object> response = new HashMap<>();
        log.info("Received payment initiation request for {} items.",
                request.getItems() != null ? request.getItems().size() : 0);
        log.debug("Payment initiation payload: {}", request);

        if (request.getItems() == null || request.getItems().isEmpty()) {
            log.warn("Payment initiation request received with no items.");
            response.put("error", "No items provided for payment.");
            return ResponseEntity.badRequest().body(response);
        }

        List<FileOrderItem> validItems = new ArrayList<>();
        JSONArray itemsJsonArray = new JSONArray();

        // 1. Validate Items
        for (FileOrderItem item : request.getItems()) {
            if (item.getFileName() == null || item.getFileName().isBlank() || item.getPageCount() < 0
                    || item.getNumberOfCopies() <= 0 || (item.getPrintType() != 0 && item.getPrintType() != 1)) {
                log.error("Invalid item data received in payment request: {}", item);
                response.put("error",
                        "Invalid data for item ID: " + item.getUniqueId() + ". File details might be incorrect.");
                return ResponseEntity.badRequest().body(response);
            }
            validItems.add(item);

            JSONObject itemJson = new JSONObject();
            itemJson.put("fName", item.getFileName());
            itemJson.put("pCount", item.getPageCount());
            itemJson.put("pType", item.getPrintType());
            itemJson.put("copies", item.getNumberOfCopies());
            itemsJsonArray.put(itemJson);
        }

        // 2. Calculate Total Amount using NEW Aggregated Logic
        int totalAmountInRupees = calculateAggregatedAmount(validItems);

        if (totalAmountInRupees < 0) {
            log.error("Total calculated amount is negative ({})! Cannot create order.", totalAmountInRupees);
            response.put("error", "Total calculated amount is invalid.");
            return ResponseEntity.badRequest().body(response);
        }
        log.info("Total calculated AGGREGATED amount for {} valid items: Rs. {}", validItems.size(),
                totalAmountInRupees);

        // 3. Create Razorpay Order or Handle Zero Amount
        try {
            String currency = "INR";
            String receiptId = "receipt_" + System.currentTimeMillis() + "_"
                    + UUID.randomUUID().toString().substring(0, 4);

            if (totalAmountInRupees == 0) {
                // Handle Zero Amount Order (skip payment, fulfill directly)
                log.info("Total amount is 0. Skipping Razorpay order creation. Triggering post-payment actions.");
                response.put("orderId", "ORDER_SKIPPED_ZERO_AMOUNT_" + receiptId);
                response.put("amount", 0);
                response.put("currency", currency);
                response.put("razorpayKey", razorpayApiKey);
                response.put("status", "skipped_zero_amount");
                response.put("totalAmountRupees", 0);
                handlePostPaymentActions(validItems, "SKIPPED_" + receiptId);
                return ResponseEntity.ok(response);
            }

            // Proceed with Razorpay for non-zero amount
            JSONObject notes = new JSONObject();
            notes.put("item_count", validItems.size());
            String itemsJsonString = itemsJsonArray.toString();
            if (itemsJsonString.length() > 1800) {
                log.warn("Items JSON string length ({}) is large. Storing truncated note.", itemsJsonString.length());
                notes.put("items_truncated", "Data exceeds notes limit. See receipt " + receiptId);
            } else {
                notes.put("items", itemsJsonString);
            }

            String orderJsonString = razorpayService.createOrder(totalAmountInRupees, currency, receiptId, notes);
            log.debug("Razorpay Order Created Raw: {}", orderJsonString);

            JSONObject orderJson = new JSONObject(orderJsonString);
            String orderId = orderJson.getString("id");
            int orderAmountPaise = orderJson.getInt("amount");

            response.put("orderId", orderId);
            response.put("amount", orderAmountPaise);
            response.put("currency", currency);
            response.put("razorpayKey", razorpayApiKey);
            response.put("status", "created");
            response.put("totalAmountRupees", totalAmountInRupees); // Send back calculated total

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

    // --- NEW Helper Method: Calculate Aggregated Amount ---
    private int calculateAggregatedAmount(List<FileOrderItem> items) {
        long totalBwPageInteractions = 0;
        long totalColorPageInteractions = 0;

        if (items == null || items.isEmpty())
            return 0;

        for (FileOrderItem item : items) {
            if (item.getPageCount() > 0) {
                long pageInteractions = (long) item.getPageCount() * item.getNumberOfCopies();
                if (item.getPrintType() == 0) { // B&W Selected
                    totalBwPageInteractions += pageInteractions;
                } else { // Color Selected
                    totalColorPageInteractions += pageInteractions;
                }
            }
        }
        log.debug("Aggregated Page Interactions -> B&W: {}, Color: {}", totalBwPageInteractions,
                totalColorPageInteractions);

        int totalBwPrice = calculateBwTieredPrice(totalBwPageInteractions);
        int totalColorPrice = calculateColorTieredPrice(totalColorPageInteractions);

        int finalTotal = totalBwPrice + totalColorPrice;
        log.info("Calculated Aggregated Price -> B&W: Rs. {}, Color: Rs. {}, Final Total: Rs. {}", totalBwPrice,
                totalColorPrice, finalTotal);
        return finalTotal;
    }

    // --- NEW Tiered Pricing Methods ---
    /**
     * B&W: <=2 pages -> 10 Rs/page. >2 pages -> 20 Rs (for first 2) + 3 Rs/page
     * (for rest)
     */
    private int calculateBwTieredPrice(long totalPages) {
        if (totalPages <= 0)
            return 0;
        return (totalPages <= 2) ? (int) totalPages * 10 : 20 + (int) (totalPages - 2) * 3;
    }

    /**
     * Color: <=2 pages -> 15 Rs/page. >2 pages -> 30 Rs (for first 2) + 6 Rs/page
     * (for rest)
     */
    private int calculateColorTieredPrice(long totalPages) {
        if (totalPages <= 0)
            return 0;
        return (totalPages <= 2) ? (int) totalPages * 15 : 30 + (int) (totalPages - 2) * 6;
    }

    // --- Webhook Endpoint (No changes needed in logic here) ---
    @PostMapping("/api/payments/webhook")
    public ResponseEntity<String> handleRazorpayWebhook(@RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature) {
        log.info("Received Razorpay Webhook Request");
        log.debug("Webhook Payload: {}", payload);
        log.debug("Webhook Signature: {}", signature);
        if (razorpayWebhookSecret == null || razorpayWebhookSecret.isBlank()) {
            log.error("Razorpay Webhook Secret is not configured.");
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
                if (paymentPayload == null) {
                    log.error("Webhook Error: Missing 'payload' object.");
                    return ResponseEntity.badRequest().body("Missing payload");
                }
                JSONObject payment = paymentPayload.optJSONObject("payment");
                if (payment == null) {
                    log.error("Webhook Error: Missing 'payment' object in payload.");
                    return ResponseEntity.badRequest().body("Missing payment payload");
                }
                JSONObject entity = payment.optJSONObject("entity");
                if (entity == null) {
                    log.error("Webhook Error: Missing 'entity' object in payment payload.");
                    return ResponseEntity.badRequest().body("Missing payment entity");
                }
                String orderId = entity.optString("order_id");
                String paymentId = entity.optString("id");
                JSONObject notes = entity.optJSONObject("notes");
                log.info("Processing 'payment.captured' for Order ID: {}, Payment ID: {}", orderId, paymentId);
                if (notes == null || (!notes.has("items") && !notes.has("items_truncated"))) {
                    log.error(
                            "Webhook Error: Missing 'notes' or expected keys ('items'/'items_truncated') in notes for order_id: {}",
                            orderId);
                    return ResponseEntity
                            .ok("Webhook processed (acknowledged), but required item data missing in notes.");
                }
                List<FileOrderItem> itemsToProcess = new ArrayList<>();
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
                            if (item.getFileName() == null || item.getPageCount() == -1 || item.getPrintType() == -1
                                    || item.getNumberOfCopies() == -1) {
                                log.error(
                                        "Webhook Error: Incomplete item data parsed from notes for order_id: {}, item index: {}, data: {}",
                                        orderId, i, itemJson);
                                continue;
                            }
                            itemsToProcess.add(item);
                        }
                        log.info("Successfully parsed {} items from 'items' note for order ID: {}",
                                itemsToProcess.size(), orderId);
                    } catch (Exception e) {
                        log.error("Webhook Error: Failed to parse 'items' JSON string from notes for order_id: {}.",
                                orderId, e);
                        return ResponseEntity.badRequest().body("Error parsing payment notes (items)");
                    }
                } else if (notes.has("items_truncated")) {
                    log.error(
                            "Webhook Error: Item data was truncated in notes for order_id: {}. Manual intervention required.",
                            orderId);
                    return ResponseEntity.ok(
                            "Webhook processed (acknowledged), but item data was truncated. Manual processing needed.");
                }
                if (itemsToProcess.isEmpty()) {
                    log.warn("Webhook Warning: No valid items found to process after parsing notes for order_id: {}",
                            orderId);
                    return ResponseEntity.ok("Webhook processed, but no valid items found in notes.");
                }
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error processing webhook");
        }
    }

    // --- handlePostPaymentActions, sendEmail, deleteFile methods (Keep as they
    // are) ---
    private void handlePostPaymentActions(List<FileOrderItem> itemsToProcess, String orderReference) {
        int successCount = 0;
        int failureCount = 0;
        log.info("Starting post-payment actions for {} items. Reference: {}", itemsToProcess.size(), orderReference);
        for (FileOrderItem item : itemsToProcess) {
            try {
                log.info("Processing item: File='{}', Type={}, Copies={}, Pages={}", item.getFileName(),
                        item.getPrintType(), item.getNumberOfCopies(), item.getPageCount());
                sendEmailWithAttachment(item.getFileName(), item.getPrintType(), item.getNumberOfCopies(),
                        item.getPageCount());
                deleteFile(item.getFileName());
                successCount++;
                log.info("Successfully processed post-payment actions for file: {}", item.getFileName());
            } catch (MessagingException | IOException e) {
                failureCount++;
                log.error("Post-Payment Action Error: Failed (email/delete) for file: {} (Reference: {}). Error: {}",
                        item.getFileName(), orderReference, e.getMessage());
            }
        }
        log.info("Post-payment actions complete for Reference: {}. Success: {}, Failures: {}", orderReference,
                successCount, failureCount);
    }

    private void sendEmailWithAttachment(String fileName, int printType, int copies, int pages)
            throws MessagingException {
        Path filePath = Paths.get(uploadDir).resolve(fileName);
        log.info("Preparing email for: File='{}', Path='{}', Type={}, Copies={}, Pages={}", fileName, filePath,
                printType, copies, pages);
        if (!Files.exists(filePath)) {
            log.error("File not found for email attachment: {}", filePath);
            throw new MessagingException("Attachment file not found: " + fileName);
        }
        if (mailUsername == null || mailUsername.isBlank() || printerEmail == null || printerEmail.isBlank()) {
            log.error("Email 'from' address or 'to' address not configured.");
            throw new MessagingException("Email configuration missing (sender or recipient).");
        }
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailUsername);
            helper.setTo(printerEmail);
            String typeStr = (printType == 0) ? "B&W" : "Color";
            String subject = String.format("Print Request: %s - %d Pages - %d Copies - %s", typeStr, pages, copies,
                    fileName);
            helper.setSubject(subject);
            String emailBody = String.format(
                    "Print Request Details:\n\nFile: %s\nType: %s\nPages: %d\nCopies: %d\n\nPlease find the file attached.",
                    fileName, typeStr, pages, copies);
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


    @PostMapping("/api/payments/webhook/QrCode")
    public ResponseEntity<Map<String, Object>> handlePaymentWebhook(@RequestBody String payload) {
        log.info("QR CODE Webhook received");
        
        try {
            JSONObject payloadJson = new JSONObject(payload);
            String event = payloadJson.optString("event");

            if ("payment.captured".equals(event)) {
                // Navigate the payload structure
                JSONObject paymentPayload = payloadJson.getJSONObject("payload");
                JSONObject payment = paymentPayload.getJSONObject("payment");
                JSONObject entity = payment.getJSONObject("entity");
                
                // Extract amount (Razorpay sends amount in paise)
                int amountPaise = entity.getInt("amount");
                int receivedAmount = amountPaise / 100; // Convert to rupees

                log.info("Payment captured - Amount: ₹{}", receivedAmount);

                // Broadcast to WebSocket clients
                paymentWebSocketHandler.broadcastPayment(receivedAmount);

                // Return response
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Payment processed",
                    "amount", receivedAmount
                ));
            } else {
                log.info("Ignoring non-payment event: {}", event);
                return ResponseEntity.ok(Map.of("status", "ignored", "event", event));
            }
        } catch (Exception e) {
            log.error("Error processing QR webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid payload structure"));
        }
    }

    @GetMapping("/received")
    public String showPaymentNotification(Model model) { // Inject the Model
        // We no longer need to pass the amount through the model here
        return "payment_notification";
    }


    // --- Static Page Mappings ---
    @GetMapping("/terms")
    public String terms() {
        return "terms";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/cancellation")
    public String cancellation() {
        return "cancellation";
    }

    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }

    @GetMapping("/google0062e0de736cb797.html")
    public String owner() {
        return "google0062e0de736cb797";
    }
}

