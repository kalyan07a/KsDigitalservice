package com.pdf.printer.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf.printer.config.PaymentWebSocketHandler; // Import the WebSocket handler
import com.pdf.printer.dto.FileOrderItem;
import com.pdf.printer.dto.PaymentInitiationRequest;
import com.pdf.printer.service.PaymentService;
import com.pdf.printer.service.RazorpayService;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Controller
@RequestMapping("/print")
public class PaymentController {

	 
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);


	@Value("${b_min_pages}")
	private int b_min_pages;
	
	@Value("${b_cost_min_pages}")
	private int b_cost_min_pages;
	
	@Value("${b_cost_remaining}")
	private int b_cost_remaining;
	
	@Value("${c_min_pages}")
	private int c_min_pages;
	
	@Value("${c_cost_min_pages}")
	private int c_cost_min_pages;
	
	@Value("${c_cost_remaining}")
	private int c_cost_remaining;
    
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
    private final PaymentService paymentService;
    private final PaymentWebSocketHandler paymentWebSocketHandler;
    

    @Autowired
    public PaymentController(RazorpayService razorpayService, JavaMailSender mailSender,
            PaymentWebSocketHandler paymentWebSocketHandler,PaymentService paymentService) {
        this.razorpayService = razorpayService;
        this.mailSender = mailSender;
        this.paymentWebSocketHandler=paymentWebSocketHandler;
        this.paymentService = paymentService;
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
            itemJson.put("printerId", item.getPrinterId());
            itemsJsonArray.put(itemJson);
        }
        
        	log.info("valid items are "+validItems);
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
            log.info("before creating order"+itemsJsonString);

            String orderJsonString = razorpayService.createOrder(totalAmountInRupees, currency, receiptId, notes);
            log.debug("Razorpay Order Created Raw: {}", orderJsonString);

            JSONObject orderJson = new JSONObject(orderJsonString);
            String orderId = orderJson.getString("id");
            int orderAmountPaise = orderJson.getInt("amount");

            response.put("orderId", orderId);
            response.put("amount", orderAmountPaise);
            response.put("currency", currency);
            response.put("razorpayKey", razorpayApiKey);
            response.put("status", "created");//bug
            response.put("totalAmountRupees", totalAmountInRupees); // Send back calculated total
//bypass payment
            //handlePostPaymentActions(validItems,"test_124");
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
     * B&W: <=5 pages -> 10 Rs/page. >5 pages -> 50 Rs (for first 5) + 5 Rs/page (for rest)
     * 
     */
    private int calculateBwTieredPrice(long totalPages) {
        if (totalPages <= 0)
            return 0;
        return (totalPages <= b_min_pages) ? (int) totalPages * b_cost_min_pages : b_min_pages*b_cost_min_pages + (int) (totalPages - b_min_pages) * b_cost_remaining;
    }

    /**
     * Color: <=3 pages -> 15 Rs/page. >3 pages -> 45 Rs (for first 3) + 8 Rs/page (for rest)
     */
    private int calculateColorTieredPrice(long totalPages) {
        if (totalPages <= 0)
            return 0;
        return (totalPages <= c_min_pages) ? (int) totalPages * c_cost_min_pages : c_min_pages*c_cost_min_pages + (int) (totalPages - c_min_pages) * c_cost_remaining;
    }

    // --- Webhook Endpoint (No changes needed in logic here) ---
    @PostMapping("/api/payments/webhook")
    public ResponseEntity<String> handleRazorpayWebhook(@RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature) {
        log.info("Received Razorpay Webhook Request");
        log.debug("Webhook Payload: {}", payload);
        String jsonStr = payload;

        ObjectMapper mapper = new ObjectMapper();
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
            	log.info("inside captured");
                JSONObject paymentPayload = payloadJson.optJSONObject("payload");
                log.info("after paymentPayload conversion"+paymentPayload);
                if (paymentPayload == null) {
                    log.error("Webhook Error: Missing 'payload' object.");
                    return ResponseEntity.badRequest().body("Missing payload");
                }
                JSONObject payment = paymentPayload.optJSONObject("payment");
                log.info("payment object"+payment);
                if (payment == null) {
                    log.error("Webhook Error: Missing 'payment' object in payload.");
                    return ResponseEntity.badRequest().body("Missing payment payload");
                }
                JSONObject entity = payment.optJSONObject("entity");
                log.info("entity object"+entity);
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
                            item.setPrinterId(itemJson.optInt("printerId", -1));
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
                //saving in db
                
                log.info("before amount calculating");
                String amount = entity.optString("amount");
                log.info("after amount calculating");
                BigDecimal bigDecimalAmount = new BigDecimal(amount);
                bigDecimalAmount = bigDecimalAmount.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                log.info("before time");
                

                String order_timestamp="" ;
                
                try {
                    JsonNode rootNode = mapper.readTree(jsonStr);
                    log.info("json is "+rootNode);
                    // Navigate through the nested JSON structure
                    JsonNode payload1 = rootNode.path("payload");
                    log.info("payload1 is "+payload1);
                    JsonNode payment1 = payload1.path("payment");
                    log.info("payment1 is "+payment1);
                    JsonNode entity1 = payment1.path("entity");
                    log.info("entity1 is "+entity1);
                    JsonNode notes1 = entity1.path("notes");
                    log.info("notes1 is "+notes1);
                    order_timestamp = notes1.path("order_timestamp").asText();

                   log.info("order_timestamp: " + order_timestamp);

                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                
                
                
                
                log.info("Parsing order_timestamp: {}", order_timestamp);
                LocalDate date;
                LocalTime time;
                if (order_timestamp == null || order_timestamp.isEmpty()) {
                    log.error("Invalid/missing order_timestamp");
                    throw new IllegalArgumentException("Timestamp is required");
                }

                try {
                    // Parse the timestamp as UTC
                    DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
                    ZonedDateTime utcZdt = ZonedDateTime.parse(order_timestamp, formatter);

                    // Convert to IST timezone
                    ZonedDateTime istZdt = utcZdt.withZoneSameInstant(ZoneId.of("Asia/Kolkata"));

                    // Extract date/time in IST
                     date = istZdt.toLocalDate();
                     time = istZdt.toLocalTime();

                    log.info("Parsed date (IST): {}, time (IST): {}", date, time);
                } catch (DateTimeParseException e) {
                    log.error("Failed to parse timestamp: {}", order_timestamp, e);
                    throw new RuntimeException("Invalid timestamp format", e);
                }
                String phone = Printer.getPhoneById(String.valueOf(itemsToProcess.get(0).getPrinterId()));
                 paymentService.recordPayment(paymentId,date,time,bigDecimalAmount,phone);
                 
                
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
    
    

    //---------------------------------------------------------------
    public void handlePostPaymentActions(List<FileOrderItem> itemsToProcess, String orderReference) {
        if (itemsToProcess == null || itemsToProcess.isEmpty()) {
            log.info("No items to process for order reference: {}", orderReference);
            return;
        }

        log.info("Starting post-payment actions for {} items. Reference: {}", itemsToProcess.size(), orderReference);

        boolean emailSentSuccessfully = false;
        try {
            // Send one email with all attachments
            sendBulkEmailWithAttachments(itemsToProcess, orderReference);
            emailSentSuccessfully = true;
            log.info("Bulk email sent successfully for order reference: {}", orderReference);
        } catch (MessagingException e) {
            log.error("Failed to send bulk email for order reference: {}. Error: {}", orderReference, e.getMessage(), e);
            // Decide if processing should stop if email fails. Current logic continues to deletion.
        } catch (Exception e) {
            // Catching unexpected errors during email sending
            log.error("Unexpected error during bulk email sending for order reference: {}. Error: {}", orderReference, e.getMessage(), e);
        }

        // Proceed with deletion attempts after trying to send the email.
        int successCount = 0;
        int failureCount = 0;
        log.info("Starting file deletion process for order reference: {}", orderReference);
        for (FileOrderItem item : itemsToProcess) {
            try {
                // Assuming deleteFile method exists and handles its own logging/errors
                deleteFile(item.getFileName());
                successCount++;
                // Optional: log individual success if needed:
                // log.debug("Successfully deleted file: {} for order reference: {}", item.getFileName(), orderReference);
            } catch (IOException e) {
                failureCount++;
                log.error("Failed to delete file: {} for order reference: {}. Error: {}", item.getFileName(), orderReference, e.getMessage());
            }
        }

        // Final status log
        if (emailSentSuccessfully) {
            log.info("Post-payment actions complete for Reference: {}. Email Status: OK. Deletions - Success: {}, Failures: {}",
                    orderReference, successCount, failureCount);
        } else {
             log.warn("Post-payment actions complete for Reference: {}. Email Status: FAILED. Deletions - Success: {}, Failures: {}",
                    orderReference, successCount, failureCount);
        }
    }

    /**
     * Sends a single email containing multiple file attachments based on the list of items.
     *
     * @param items          List of file order items.
     * @param orderReference The order reference for context.
     * @throws MessagingException If email configuration is missing, attachment fails critically, or sending fails.
     */
    private void sendBulkEmailWithAttachments(List<FileOrderItem> items, String orderReference) throws MessagingException {
        // Ensure email configuration is present
    	String printerEmail = Printer.getEmailById(String.valueOf(items.get(0).getPrinterId()));
    	log.info("printer email is"+ printerEmail);
        if (mailUsername == null || mailUsername.isBlank() || printerEmail == null || printerEmail.isBlank()) {
            log.error("Email 'from' address or 'to' address not configured for order reference: {}", orderReference);
            throw new MessagingException("Email configuration missing (sender or recipient).");
        }

        MimeMessage message = mailSender.createMimeMessage();
        try {
            // Use multipart helper for attachments
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailUsername);
            helper.setTo(printerEmail);

            // --- Construct Subject ---
            // Subject reflects the number of *unique* files, not total attachments
            String subject = String.format("Bulk Print Request: %s - %d Unique File(s)", orderReference, items.size());
            helper.setSubject(subject);

            // --- Construct Body ---
            StringBuilder emailBody = new StringBuilder();
            emailBody.append(String.format("Bulk Print Request - Order Reference: %s\n\n", orderReference));
            emailBody.append(String.format("Total Unique Files: %d\n\n", items.size()));
            emailBody.append("File Details (Copies requested indicate total prints needed per file):\n");
            emailBody.append("--------------------------------------------------------------------\n");

            List<String> failedAttachments = new ArrayList<>(); // Keep track of files where *any* copy failed
            int totalAttachmentsAttempted = 0;

            // --- Add Attachments and Build Body Details ---
            for (FileOrderItem item : items) {
                String originalFileName = item.getFileName();
                int numberOfCopies = item.getNumberOfCopies();
                Path filePath = Paths.get(uploadDir).resolve(originalFileName);
                String typeStr = (item.getPrintType() == 0) ? "B&W" : "Color";
                boolean anyCopyFailedForItem = false; // Flag for failures related to this specific item

                // Add item details to the body (shows requested copies)
                emailBody.append(String.format("- File: %s | Type: %s | Pages: %d | Copies Requested: %d\n",
                        originalFileName, typeStr, item.getPageCount(), numberOfCopies));

                log.info("Preparing attachment(s) for: File='{}', Path='{}', Copies={}", originalFileName, filePath, numberOfCopies);

                // Check if the source file exists *before* attempting to attach copies
                if (!Files.exists(filePath)) {
                    log.error("Attachment source file not found: {} for order reference: {}", filePath, orderReference);
                    failedAttachments.add(originalFileName + " (Source File Not Found)");
                    anyCopyFailedForItem = true; // Mark this item as failed
                    // Continue to the next FileOrderItem
                    continue; // Skip the inner loop for this item
                }

                // --- Inner loop to attach the required number of copies ---
                for (int i = 1; i <= numberOfCopies; i++) {
                    totalAttachmentsAttempted++;
                    String attachmentFileName = originalFileName; // Default to original name

                    // Modify attachment name for copies > 1 to ensure uniqueness in the email
                    if (numberOfCopies > 1) {
                        int dotIndex = originalFileName.lastIndexOf('.');
                        if (dotIndex > 0 && dotIndex < originalFileName.length() - 1) { // Check if dot exists and is not the last char
                            String baseName = originalFileName.substring(0, dotIndex);
                            String extension = originalFileName.substring(dotIndex); // Includes the dot
                            attachmentFileName = String.format("%s (copy %d)%s", baseName, i, extension);
                        } else {
                            // Handle files with no extension or dot at the end
                            attachmentFileName = String.format("%s (copy %d)", originalFileName, i);
                        }
                    }

                    log.debug("Attempting to attach copy {}/{} as '{}' for original file '{}'", i, numberOfCopies, attachmentFileName, originalFileName);

                    try {
                        // Add the file as an attachment with the potentially modified name
                        helper.addAttachment(attachmentFileName, filePath.toFile());
                        log.debug("Successfully added attachment copy: {}", attachmentFileName);
                    } catch (MessagingException e) {
                        log.error("Failed to add attachment copy: '{}' (copy {} of {}) for order reference: {}. Error: {}",
                                attachmentFileName, i, numberOfCopies, orderReference, e.getMessage());
                        // If *any* copy fails for an item, add the *original* filename to the failure list
                        if (!anyCopyFailedForItem) { // Add only once per original file
                            failedAttachments.add(originalFileName + " (Attachment Error on copy " + i + ": " + e.getMessage() + ")");
                            anyCopyFailedForItem = true;
                        }
                        // Decide if one failed copy should stop attaching other copies of the *same* file.
                        // For now, we log, record failure, and continue trying subsequent copies (if any).
                        // If you want to stop attaching further copies of THIS file upon first failure, add 'break;' here:
                        // break;
                    } catch (Exception e) { // Catch unexpected errors during attachment
                         log.error("Unexpected error attaching copy: '{}' (copy {} of {}) for order reference: {}. Error: {}",
                                attachmentFileName, i, numberOfCopies, orderReference, e.getMessage(), e);
                         if (!anyCopyFailedForItem) {
                            failedAttachments.add(originalFileName + " (Unexpected Attachment Error on copy " + i + ": " + e.getMessage() + ")");
                            anyCopyFailedForItem = true;
                         }
                         // break; // Optional: stop attaching further copies of this file
                    }
                } // --- End of inner loop for copies ---
            } // --- End of loop through items ---

            emailBody.append("--------------------------------------------------------------------\n");
            emailBody.append(String.format("Total attachment attempts: %d\n\n", totalAttachmentsAttempted));


            // --- Add Warning for Failed Attachments ---
            if (!failedAttachments.isEmpty()) {
                 emailBody.append("WARNING: Could not attach one or more copies for the following original files:\n");
                 failedAttachments.forEach(fail -> emailBody.append("- ").append(fail).append("\n"));
                 emailBody.append("\nPlease check the source files and system logs.\n\n");
            }

            emailBody.append("Please find the successfully attached file(s) for printing according to the details above.");
            helper.setText(emailBody.toString());

            // --- Send the Email ---
            log.info("Sending bulk email to {} with subject: '{}' for order reference: {}. Attempting to send {} attachments.",
                     printerEmail, subject, orderReference, totalAttachmentsAttempted - failedAttachments.size()); // Log successful attempts maybe? Or total.
            mailSender.send(message);
            log.info("Bulk email successfully queued for sending for order reference: {}", orderReference);

            // Optional: If you require *all* copies of *all* files to be attached successfully, check here.
            // if (!failedAttachments.isEmpty()) {
            //    throw new MessagingException("One or more files (or their copies) could not be attached: " + String.join("; ", failedAttachments));
            // }

        } catch (MessagingException e) {
            // Catch errors during MimeMessageHelper setup or mailSender.send()
            log.error("Failed to prepare or send bulk email for order reference: {}", orderReference, e);
            throw e; // Re-throw to indicate failure to the caller
        } catch (Exception e) {
            // Catch unexpected errors during the process
            log.error("Unexpected error during bulk email preparation/sending for order reference: {}", orderReference, e);
            // Wrap in MessagingException or a custom exception type
            throw new MessagingException("Unexpected error sending bulk email: " + e.getMessage(), e);
        }
    }

    // Make sure the 'deleteFile(String fileName)' method exists in your class as well.
    // private void deleteFile(String fileName) throws IOException { ... }

    
    //----------------------------------------------------------------

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



}

