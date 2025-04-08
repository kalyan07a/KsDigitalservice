package com.pdf.printer.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import com.pdf.printer.dto.PaymentInitiationRequest;
import com.pdf.printer.service.RazorpayService;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.Authenticator;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

@Controller
@RequestMapping("/printer")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${username}")
    private String mailUsername;

    @Value("${password}")
    private String mailPassword;

    @Value("${p-email}")
    private String printerEmail;

    @Value("${razorpay.api.key}") // Needed for frontend response
    private String razorpayApiKey;

    @Value("${razorpay.webhook.secret}") // Needed for webhook verification
    private String razorpayWebhookSecret;

    private final RazorpayService razorpayService; // Use final for constructor injection

    // --- Constructor Injection ---
    @Autowired // Optional in newer Spring, but good practice
    public PaymentController(RazorpayService razorpayService) {
        this.razorpayService = razorpayService;
        log.info("PaymentController created and RazorpayService injected.");
    }

    @PostMapping("/api/payments/initiate")
    public ResponseEntity<Map<String, Object>> initiatePayment(@RequestBody PaymentInitiationRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            log.info("DEBUG: Received PaymentInitiationRequest: {}", request);

            // Validate input from request
            if (request.getFileName() == null || request.getFileName().isBlank()) {
                log.error("Missing filename in payment request.");
                response.put("error", "Missing filename for payment initiation.");
                return ResponseEntity.badRequest().body(response);
            }
            if (request.getPageCount() <= 0) {
                log.error("Invalid page count in payment request: {}", request.getPageCount());
                response.put("error", "Invalid page count.");
                return ResponseEntity.badRequest().body(response);
            }
             if (request.getNumberOfCopies() <= 0) {
                log.error("Invalid number of copies in payment request: {}", request.getNumberOfCopies());
                response.put("error", "Invalid number of copies.");
                return ResponseEntity.badRequest().body(response);
            }
             if (request.getPrintType() != 0 && request.getPrintType() != 1) {
                 log.error("Invalid print type in payment request: {}", request.getPrintType());
                 response.put("error", "Invalid print type.");
                 return ResponseEntity.badRequest().body(response);
             }


            int pages = request.getPageCount();
            int printType = request.getPrintType();
            int copies = request.getNumberOfCopies(); // Use the correct getter
            log.info("DEBUG: Values received - pages: {}, printType: {}, copies: {}", pages, printType, copies);

            // Calculate amount using the backend logic
            int amountInRupees = calculateAmount(pages, printType, copies);
            log.info("DEBUG: Calculated amount (Rupees): {}", amountInRupees);

            if (amountInRupees <= 0) {
                log.error("Invalid amount calculation (pages={}, type={}, copies={}) resulted in amount={}", pages, printType, copies, amountInRupees);
                response.put("error", "Invalid calculated amount (pages=" + pages + ").");
                return ResponseEntity.badRequest().body(response);
            }

            String currency = "INR";
            String receiptId = "receipt_" + System.currentTimeMillis();

            // Create notes for Razorpay order
            JSONObject notes = new JSONObject();
            notes.put("fileName", request.getFileName()); // Final filename (color or bw)
            notes.put("printType", String.valueOf(printType));
            notes.put("numberOfCopies", String.valueOf(copies)); // Add copies to notes
            notes.put("pageCount", String.valueOf(pages)); // Add page count to notes

            // Call the injected RazorpayService
            String orderJsonString = this.razorpayService.createOrder(amountInRupees, currency, receiptId, notes);
            log.info("Razorpay Order Created Raw: {}", orderJsonString);

            JSONObject orderJson = new JSONObject(orderJsonString);
            String orderId = orderJson.getString("id");
            int orderAmountPaise = orderJson.getInt("amount"); // Amount from Razorpay response (in paise)

            // Prepare response for the frontend
            response.put("orderId", orderId);
            response.put("amount", orderAmountPaise); // Send amount in paise, as expected by Razorpay Checkout
            response.put("currency", currency);
            response.put("razorpayKey", razorpayApiKey); // Your public Key ID
            response.put("status", "created");

            return ResponseEntity.ok(response);

        } catch (RazorpayException e) {
            log.error("RazorpayException during payment initiation: {}", e.getMessage(), e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Payment gateway error";
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("authentication failed")) {
                errorMessage = "Razorpay Authentication Failed. Please check server configuration.";
                 log.error("!!! Razorpay Authentication Failed - Verify Key ID and Key Secret in configuration !!!");
            }
            response.put("error", "Payment Error: " + errorMessage);
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
            log.error("Unexpected error during payment initiation processing", e);
            response.put("error", "An unexpected server error occurred.");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // --- Webhook Endpoint ---
    @PostMapping("/api/payments/webhook")
    public ResponseEntity<String> handleRazorpayWebhook(@RequestBody String payload, @RequestHeader("X-Razorpay-Signature") String signature) {
        log.info("Received Razorpay Webhook Request");
        log.debug("Webhook Payload: {}", payload); // Log payload only if needed for debugging sensitive data
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
            String event = payloadJson.getString("event");
            log.info("Webhook event type: {}", event);

            // Process only successful payment events
            if ("payment.captured".equals(event)) {
                JSONObject paymentEntity = payloadJson.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
                String orderId = paymentEntity.getString("order_id");
                String paymentId = paymentEntity.getString("id");
                JSONObject notes = paymentEntity.optJSONObject("notes");

                if (notes == null) {
                    log.error("Webhook Error: Missing 'notes' in payment entity for order_id: {}", orderId);
                    // Depending on policy, might still return OK to Razorpay, but log error
                    return ResponseEntity.badRequest().body("Missing payment notes");
                }

                // Extract necessary details from notes
                String fileNameToSend = notes.optString("fileName", null);
                int printType = notes.optInt("printType", -1); // Default to -1 if missing/invalid
                int copies = notes.optInt("numberOfCopies", -1); // Default to -1
                int pages = notes.optInt("pageCount", -1); // Default to -1

                log.info("Processing 'payment.captured' webhook for Order ID: {}, Payment ID: {}, File: {}, Type: {}, Copies: {}, Pages: {}",
                         orderId, paymentId, fileNameToSend, printType, copies, pages);

                if (fileNameToSend == null || printType == -1 || copies == -1 || pages == -1) {
                     log.error("Webhook Error: Missing essential data (fileName, printType, numberOfCopies, pageCount) in notes for order_id: {}", orderId);
                     return ResponseEntity.badRequest().body("Incomplete payment notes");
                }

                // Perform Post-Payment Actions (Email, File Deletion)
                try {
                    sendEmailWithAttachment(fileNameToSend, printType, copies, pages);
                    // Consider what exactly to delete. Just the sent file is simplest.
                    // If you need to delete the original or other generated files,
                    // you might need more info stored in the notes during order creation.
                    deleteFile(fileNameToSend);

                    log.info("Successfully processed webhook and post-payment actions for Order ID: {}", orderId);
                    // Return OK to Razorpay to acknowledge receipt
                    return ResponseEntity.ok("Webhook processed successfully");

                } catch (MessagingException | IOException e) {
                    log.error("Webhook Processing Error: Failed during post-payment actions (email/delete) for Order ID: {}", orderId, e);
                    // Return an error, Razorpay might retry
                    return ResponseEntity.internalServerError().body("Error during post-payment processing");
                }
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

    // --- Helper Method: Calculate Amount (Same as frontend logic) ---
    private int calculateAmount(int pages, int printType, int numberOfCopies) {
        log.info("Calculating amount for pages={}, printType={}, copies={}", pages, printType, numberOfCopies);
        if (pages <= 0 || numberOfCopies < 1 || (printType != 0 && printType != 1)) {
            log.warn("Invalid input for amount calculation: pages={}, printType={}, copies={}", pages, printType, numberOfCopies);
            return 0;
        }

        int firstCopyPrice;
        if (printType == 0) { // Black and White - First Copy
            firstCopyPrice = (pages <= 2) ? pages * 10 : (pages - 2) * 2 + 20;
        } else { // Color - First Copy (printType == 1)
            firstCopyPrice = (pages <= 1) ? pages * 20 : (pages - 1) * 5 + 20;
        }
        log.debug("Calculated first copy price: {}", firstCopyPrice);

        if (numberOfCopies == 1) {
            log.info("Total amount for 1 copy: {}", firstCopyPrice);
            return firstCopyPrice;
        }

        int additionalCopyCost;
        if (printType == 0) { // Black and White - Additional Copy Cost
            additionalCopyCost = pages * 2;
        } else { // Color - Additional Copy Cost
            additionalCopyCost = pages * 5;
        }
        log.debug("Calculated additional copy cost per copy: {}", additionalCopyCost);

        int totalAmount = firstCopyPrice + (additionalCopyCost * (numberOfCopies - 1));
        log.info("Calculated total amount: {} for {} copies", totalAmount, numberOfCopies);
        return totalAmount;
    }

    // --- Helper Method: Send Email ---
    private void sendEmailWithAttachment(String fileName, int printType, int copies, int pages) throws MessagingException {
         log.info("Preparing to send email for file: {}, printType: {}, copies: {}, pages: {}", fileName, printType, copies, pages);
         final String username = mailUsername;
         final String password = mailPassword; // Use App Password for Gmail

         // Basic validation
         if (username == null || username.isBlank() || password == null || password.isBlank() || printerEmail == null || printerEmail.isBlank()) {
             log.error("Email credentials or recipient email not configured. Cannot send email.");
             throw new MessagingException("Email configuration missing.");
         }

         Properties props = new Properties();
         props.put("mail.smtp.host", "smtp.gmail.com"); // Gmail host
         props.put("mail.smtp.port", "587"); // TLS Port
         props.put("mail.smtp.auth", "true");
         props.put("mail.smtp.starttls.enable", "true"); // Use STARTTLS

         Session session = Session.getInstance(props, new Authenticator() {
             protected PasswordAuthentication getPasswordAuthentication() {
                 return new PasswordAuthentication(username, password);
             }
         });

         try {
             Message message = new MimeMessage(session);
             message.setFrom(new InternetAddress(username));
             message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(printerEmail)); // Send to printer

             // Construct Subject
             String typeStr = (printType == 0) ? "B&W" : "Color";
             String subject = String.format("Print Request: %s - %d Pages - %d Copies - %s",
                                            typeStr, pages, copies, fileName);
             message.setSubject(subject);

             // Create body part for text
             BodyPart textBodyPart = new MimeBodyPart();
             String emailBody = String.format(
                 "Print Request Details:\n\nFile: %s\nType: %s\nPages: %d\nCopies: %d\nPlease find the file attached.",
                 fileName, typeStr, pages, copies
             );
             textBodyPart.setText(emailBody);

             // Create body part for attachment
             BodyPart attachmentBodyPart = new MimeBodyPart();
             Path filePath = Paths.get(uploadDir, fileName);
             if (!Files.exists(filePath)) {
                 log.error("File not found for email attachment: {}", filePath);
                 throw new MessagingException("Attachment file not found: " + fileName);
             }
             DataSource source = new FileDataSource(filePath.toString());
             attachmentBodyPart.setDataHandler(new DataHandler(source));
             attachmentBodyPart.setFileName(fileName); // Set the filename for the attachment

             // Combine parts into multipart
             Multipart multipart = new MimeMultipart();
             multipart.addBodyPart(textBodyPart);
             multipart.addBodyPart(attachmentBodyPart);

             // Set the multipart content to the message
             message.setContent(multipart);

             log.info("Sending email to {} with subject: {}", printerEmail, subject);
             Transport.send(message);
             log.info("Email sent successfully for file: {}", fileName);

         } catch (MessagingException e) {
             log.error("Failed to send email for file: {}", fileName, e);
             throw e; // Re-throw to indicate failure in webhook processing
         }
     }


    // --- Helper Method: Delete File ---
    private void deleteFile(String fileName) throws IOException {
        Path path = Paths.get(uploadDir, fileName);
        try {
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("File deleted successfully: {}", path);
            } else {
                log.warn("File not found for deletion, skipping: {}", path);
            }
        } catch (IOException e) {
            log.error("Failed to delete file: {}", path, e);
            throw e; // Re-throw to indicate failure
        }
    }
}