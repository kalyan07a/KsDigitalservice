package com.pdf.printer.controller;

import com.pdf.printer.dto.FileInfo;
import com.pdf.printer.dto.PaymentInitiationRequest;
import com.pdf.printer.service.FileStorageService;
import com.pdf.printer.service.RazorpayService;
import com.razorpay.RazorpayException;
import com.razorpay.Utils; // Import for webhook verification

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.*;

import org.json.JSONObject; // Keep for webhook payload parsing
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Controller
@RequestMapping("/print")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    @Value("${file.upload-dir}")
    private String uploadDir; // Use consistent naming 'uploadDir'

    @Value("${username}")
    private String mailUsername;

    @Value("${password}")
    private String mailPassword;

    @Value("${p-email}")
    private String printerEmail;

    @Value("${razorpay.api.key}")
    private String razorpayApiKey;

    @Value("${razorpay.webhook.secret}")
    private String razorpayWebhookSecret; // Inject webhook secret

    private final FileStorageService fileStorageService;
    private final RazorpayService razorpayService;
    private FileInfo uploadedFileInfo;
    // Removed stateful instance variables: fileInfo, printType, amount

    @Autowired
    public FileUploadController(FileStorageService fileStorageService, RazorpayService razorpayService) {
        this.fileStorageService = fileStorageService;
        this.razorpayService = razorpayService;
    }

    @GetMapping("/")
    public String index() {
        // Assuming index.html is in src/main/resources/static
        // If using Thymeleaf, ensure it's in src/main/resources/templates
        return "index.html";
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        if (file.isEmpty()) {
            response.put("error", "Please select a file to upload.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            uploadedFileInfo = fileStorageService.storeFile(file); // Use local variable
            log.info("File uploaded: {}", uploadedFileInfo);
            response.put("url", uploadedFileInfo.getUrl());
            response.put("fileName", uploadedFileInfo.getC_fileName());
            response.put("pageCount", uploadedFileInfo.getPageCount());
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("File upload failed", e);
            response.put("error", "File upload failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
            log.error("Unexpected error during file upload", e);
            response.put("error", "An unexpected error occurred during upload.");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/api/payments/initiate")
    public ResponseEntity<Map<String, Object>> initiatePayment(@RequestBody PaymentInitiationRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            log.info("Initiating payment for: {}", request);
            int pages = request.getPageCount();
            int printType = request.getPrintType();
            int amount = calculateAmount(pages, printType); // Calculate amount based on request

            if (amount <= 0) {
                 response.put("error", "Invalid amount calculation.");
                 return ResponseEntity.badRequest().body(response);
            }

            String currency = "INR";
            String receiptId = "receipt_" + System.currentTimeMillis();

            // Include necessary details in Razorpay order notes for webhook processing
            
            JSONObject notes = new JSONObject();
            if(printType==0)
            notes.put("fileName",uploadedFileInfo.getB_fileName());
            else
            	notes.put("fileName",uploadedFileInfo.getC_fileName());
            notes.put("printType", String.valueOf(printType));// Store as string for consistency

            String orderJsonString = razorpayService.createOrder(amount, currency, receiptId, notes);
            log.info("Razorpay Order Created: {}", orderJsonString);

            // Extract order_id from the JSON string response from RazorpayService
            JSONObject orderJson = new JSONObject(orderJsonString);
            String orderId = orderJson.getString("id");

            response.put("orderId", orderId);
            response.put("amount", amount * 100); // Amount in paise for frontend
            response.put("currency", currency);
            response.put("razorpayKey", razorpayApiKey); // Send key to frontend
            response.put("status", "created"); // Or extract from orderJson if needed

            return ResponseEntity.ok(response);

        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed", e);
            response.put("error", "Payment gateway error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
            log.error("Unexpected error during payment initiation", e);
            response.put("error", "An unexpected error occurred.");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // --- Webhook Endpoint ---
    @PostMapping("/api/payments/webhook")
    public ResponseEntity<String> handleRazorpayWebhook(@RequestBody String payload, @RequestHeader("X-Razorpay-Signature") String signature) {
        log.info("Received Razorpay Webhook");
        try {
            // 1. Verify Signature (CRITICAL FOR SECURITY)
            boolean isValid = Utils.verifyWebhookSignature(payload, signature, razorpayWebhookSecret);

            if (!isValid) {
                log.warn("Invalid Razorpay webhook signature received.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
            }

            log.info("Webhook signature verified.");
            JSONObject payloadJson = new JSONObject(payload);

            // 2. Check Event Type (e.g., payment.captured)
            String event = payloadJson.getString("event");
            log.info("Webhook event: {}", event);

            if ("payment.captured".equals(event)) {
                JSONObject paymentEntity = payloadJson.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");

                // 3. Extract necessary info (e.g., order_id, payment_id, notes)
                String orderId = paymentEntity.getString("order_id");
                String paymentId = paymentEntity.getString("id");
                JSONObject notes = paymentEntity.optJSONObject("notes"); // Use optJSONObject for safety

                if (notes == null) {
                    log.error("Missing 'notes' in payment entity for order_id: {}", orderId);
                    // Decide how to handle this - maybe lookup order details elsewhere if you store them
                    return ResponseEntity.badRequest().body("Missing payment notes");
                }

                String fileName = notes.optString("fileName", null); // Use optString for safety
                int printType = notes.optInt("printType", -1); // Use optInt, provide default

                log.info("Processing successful payment webhook for Order ID: {}, Payment ID: {}, File: {}, PrintType: {}",
                         orderId, paymentId, fileName, printType);

                if (fileName == null || printType == -1) {
                     log.error("Missing fileName or printType in notes for order_id: {}", orderId);
                     return ResponseEntity.badRequest().body("Incomplete payment notes");
                }

                // 4. Perform Post-Payment Actions (Email, File Deletion)
                try {
                    sendEmailWithAttachment(fileName, printType);
                    deleteFile(fileName);
                    log.info("Successfully processed payment and sent email for Order ID: {}", orderId);
                    return ResponseEntity.ok("Webhook processed successfully");
                } catch (MessagingException | IOException e) {
                    log.error("Error processing post-payment actions for Order ID: {}", orderId, e);
                    // Consider adding to a retry queue or logging for manual intervention
                    return ResponseEntity.internalServerError().body("Error during post-payment processing");
                }

            } else {
                log.info("Ignoring webhook event: {}", event);
                return ResponseEntity.ok("Event received but not processed: " + event); // Acknowledge receipt
            }

        } catch (RazorpayException e) {
            log.error("Error verifying webhook signature", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook signature verification failed");
        } catch (Exception e) {
            log.error("Error processing webhook payload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error processing webhook");
        }
    }

    // --- Helper Methods ---

    private int calculateAmount(int pages, int printType) {
        int amount = 0;
        if (pages <= 0) return 0; // Basic validation

        if (printType == 0) { // Black and White
            amount = (pages <= 2) ? pages * 10 : (pages - 2) * 2 + 20;
        } else if (printType == 1) { // Color
            amount = (pages <= 1) ? pages * 20 : (pages - 1) * 5 + 20; // Adjusted color logic slightly (2*20=40 for first 2 pages)
        }
        log.info("Calculated amount: {} for {} pages, printType: {}", amount, pages, printType);
        return amount;
    }


    private void sendEmailWithAttachment(String fileName, int printType) throws MessagingException {
        log.info("Preparing to send email for file: {}, printType: {}", fileName, printType);

        final String username = mailUsername;
        final String password = mailPassword; // Use App Password for Gmail

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(printerEmail));

            String subject = (printType == 0)
                ? "Print Request: Black and White - " + fileName
                : "Print Request: Color - " + fileName;
            message.setSubject(subject);

            BodyPart messageBodyPart = new MimeBodyPart();
            // You can add text to the body if needed:
            // messageBodyPart.setText("Please print the attached document.");

            Multipart multipart = new MimeMultipart();
            // multipart.addBodyPart(messageBodyPart); // Add text part if you have one

            // Attachment Part
            messageBodyPart = new MimeBodyPart();
            String filePath = Paths.get(uploadDir, fileName).toString();
            DataSource source = new FileDataSource(filePath);
            messageBodyPart.setDataHandler(new DataHandler(source));
            messageBodyPart.setFileName(fileName); // Keep original filename in email
            multipart.addBodyPart(messageBodyPart);

            message.setContent(multipart);

            log.info("Sending email to {} with subject: {}", printerEmail, subject);
            Transport.send(message);
            log.info("Email sent successfully for file: {}", fileName);

        } catch (MessagingException e) {
            log.error("Failed to send email for file: {}", fileName, e);
            throw e; // Re-throw to indicate failure in the webhook handler
        }
    }

    private void deleteFile(String fileName) throws IOException {
        Path path = Paths.get(uploadDir, fileName);
        try {
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("File deleted successfully: {}", path);
            } else {
                log.warn("File not found for deletion: {}", path);
            }
        } catch (IOException e) {
            log.error("Failed to delete file: {}", path, e);
            throw e; // Re-throw to indicate failure
        }
    }

     // Remove unused /submitPrintType endpoint and its inner class PrintTypeRequest
     // Remove unused print() method
     // Remove unused extractStatusFromString method (handle JSON properly now)

}