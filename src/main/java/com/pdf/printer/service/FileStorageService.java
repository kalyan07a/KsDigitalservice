package com.pdf.printer.service;

import com.pdf.printer.dto.FileInfo;
import org.apache.pdfbox.Loader; // Use Loader for newer PDFBox
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private final Path fileStorageLocation;
    private final String uploadsBaseUrl = "/uploads/";

    @Value("${file.upload-dir}")
    private String uploadDir;

    public FileStorageService(@Value("${file.upload-dir}") String uploadDir) {
        // Ensure uploadDir is not null or empty before creating Path
         if (uploadDir == null || uploadDir.isBlank()) {
             log.error("!!! Configuration error: 'file.upload-dir' property is missing or empty! Uploads will likely fail. !!!");
             // Assign a default or throw an error? Let's assign a temporary default for Path object creation, but log heavily.
             this.uploadDir = "./temp_uploads_error/"; // Temporary default
             this.fileStorageLocation = Paths.get(this.uploadDir).toAbsolutePath().normalize();
         } else {
            this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
         }
        log.info("Attempting to initialize file storage at resolved path: {}", this.fileStorageLocation);
    }

    @PostConstruct
    public void init() throws IOException {
        // Check again if uploadDir was valid initially
        if (this.uploadDir == null || this.uploadDir.isBlank() || this.uploadDir.equals("./temp_uploads_error/")) {
             log.error("!!! PostConstruct: Cannot initialize storage. 'file.upload-dir' is not configured properly. !!!");
             // Throwing exception here will prevent application startup, which is often desired
             throw new IOException("Configuration property 'file.upload-dir' is missing or invalid.");
        }

        log.info("File storage location configured to: {}", this.fileStorageLocation);
        if (!Files.exists(this.fileStorageLocation)) {
            try {
                Files.createDirectories(this.fileStorageLocation);
                log.info("Created upload directory: {}", this.fileStorageLocation);
            } catch (IOException ex) {
                log.error("Could not create upload directory: {}", this.fileStorageLocation, ex);
                throw new IOException("Could not create upload directory: " + this.fileStorageLocation, ex);
            }
        } else {
            log.info("Upload directory already exists: {}", this.fileStorageLocation);
        }
         // Test write permission
        Path testFile = this.fileStorageLocation.resolve("permission_test_" + UUID.randomUUID().toString() + ".tmp");
        try {
             Files.createFile(testFile);
             Files.delete(testFile);
             log.info("Write permission confirmed for upload directory.");
        } catch (IOException e) {
            log.error("!!! Write permission test FAILED for upload directory: {} !!! Check filesystem permissions.", this.fileStorageLocation, e);
             throw new IOException("Write permission denied for upload directory: " + this.fileStorageLocation, e);
        }
    }

    public FileInfo storeFile(MultipartFile file, String pageRangeType, Integer startPage, Integer endPage) throws IOException, IllegalArgumentException {
        String inputOriginalFileName = StringUtils.cleanPath(file.getOriginalFilename());
         String mimeType = file.getContentType(); // Get mime type early

        if (inputOriginalFileName == null || inputOriginalFileName.isEmpty()) {
            inputOriginalFileName = "upload_" + UUID.randomUUID();
            log.warn("Original filename was empty, generated: {}", inputOriginalFileName);
        }
         String sanitizedOriginalFileName = inputOriginalFileName
            .replaceAll("\\s+", "_")
            .replaceAll("[^a-zA-Z0-9.\\-_]", "");

        if (sanitizedOriginalFileName.isEmpty()) {
             sanitizedOriginalFileName = "sanitized_" + UUID.randomUUID();
             log.warn("Sanitized filename was empty, generated: {}", sanitizedOriginalFileName);
        }

        String extension = "";
        String baseName = sanitizedOriginalFileName;
        int lastDotIndex = sanitizedOriginalFileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < sanitizedOriginalFileName.length() - 1) {
            extension = sanitizedOriginalFileName.substring(lastDotIndex).toLowerCase();
            baseName = sanitizedOriginalFileName.substring(0, lastDotIndex);
        } else {
             log.warn("File '{}' appears to have no extension based on name. Extension set to empty.", sanitizedOriginalFileName);
             // Use MimeType to infer extension if possible (basic cases)
             if (mimeType != null) {
                if (mimeType.equals("application/pdf")) extension = ".pdf";
                else if (mimeType.equals("image/jpeg")) extension = ".jpg";
                else if (mimeType.equals("image/png")) extension = ".png";
                else log.warn("Could not infer standard extension from mimeType: {}", mimeType);
             }
        }

        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String originalColorStoredFileName = "orig_" + uniqueId + "_" + baseName + extension;
        Path originalColorTargetPath = this.fileStorageLocation.resolve(originalColorStoredFileName);

        log.info("Storing original file from '{}' ({}) as: {}", inputOriginalFileName, mimeType, originalColorStoredFileName);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, originalColorTargetPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Successfully copied input stream to {}", originalColorTargetPath);
        } catch (IOException ex) {
            log.error("Could not store original file {} from input '{}'", originalColorStoredFileName, inputOriginalFileName, ex);
            throw new IOException("Could not store file " + inputOriginalFileName + ". Please try again!", ex);
        }

        Path finalColorFilePath = originalColorTargetPath;
        String finalColorFileName = originalColorStoredFileName;
        int finalPageCount = 0;
        boolean isPdf = ".pdf".equals(extension) || "application/pdf".equals(mimeType);
        boolean isImage = isImageExtension(extension) || (mimeType != null && mimeType.startsWith("image/"));


        int originalPageCount = 0;
        if (isPdf) {
            try (PDDocument document = Loader.loadPDF(originalColorTargetPath.toFile())) { // Use Loader
                originalPageCount = document.getNumberOfPages();
                log.info("Original PDF page count for '{}': {}", originalColorStoredFileName, originalPageCount);
                if (originalPageCount == 0) {
                    log.warn("PDF '{}' reported 0 pages.", originalColorStoredFileName);
                }
            } catch (Exception e) {
                log.error("Error reading original PDF '{}' for page count. File might be corrupted or password-protected.", originalColorStoredFileName, e);
            }
        } else if (isImage) {
            originalPageCount = 1;
            finalPageCount = 1;
            log.info("Image '{}' detected, page count set to 1", originalColorStoredFileName);
        } else {
             log.warn("Unsupported file type '{}' for page count/range processing: extension='{}', mimeType='{}'", originalColorStoredFileName, extension, mimeType);
             pageRangeType = "all";
             originalPageCount = 0;
             finalPageCount = 0;
        }

        if (!isImage) {
           finalPageCount = originalPageCount;
        }


        // --- Handle Custom Page Range (Only for PDFs with known page count > 0) ---
        if (isPdf && "custom".equals(pageRangeType) && startPage != null && endPage != null) {
             if (originalPageCount <= 0) {
                log.error("Cannot apply custom range: Original page count for PDF '{}' is unknown or zero.", originalColorStoredFileName);
                throw new IllegalArgumentException("Cannot determine original page count for PDF. Unable to apply custom range.");
             }
             log.info("Processing custom page range {}-{} for '{}' ({} pages)", startPage, endPage, originalColorStoredFileName, originalPageCount);

            if (startPage < 1 || endPage < startPage || endPage > originalPageCount) {
                log.error("Invalid page range requested: {}-{} for PDF '{}' with {} pages.", startPage, endPage, originalColorStoredFileName, originalPageCount);
                throw new IllegalArgumentException("Invalid page range. Start must be >= 1, End must be >= Start, and End must not exceed total pages (" + originalPageCount + ").");
            }

            String customBaseName = "custom_" + uniqueId + "_" + baseName;
            String customColorFileName = customBaseName + "_p" + startPage + "-" + endPage + extension;
            Path customColorTargetPath = this.fileStorageLocation.resolve(customColorFileName);
            try {
                createCustomPdf(originalColorTargetPath.toString(), customColorTargetPath.toString(), startPage, endPage);
                finalColorFilePath = customColorTargetPath;
                finalColorFileName = customColorFileName;
                try (PDDocument customDoc = Loader.loadPDF(customColorTargetPath.toFile())) { // Use Loader
                    finalPageCount = customDoc.getNumberOfPages();
                    int expectedCount = endPage - startPage + 1;
                    if (finalPageCount != expectedCount) {
                       log.warn("Custom PDF page count ({}) differs from expected range ({}). Using actual count.", finalPageCount, expectedCount);
                    }
                }
                log.info("Created custom color PDF: {} with {} pages", customColorFileName, finalPageCount);
            } catch (IOException e) {
                log.error("Failed to create custom PDF from {} to {}", originalColorStoredFileName, customColorFileName, e);
                throw new IOException("Failed to create custom page range PDF.", e);
            }
        } else if ("custom".equals(pageRangeType)) {
             if (!isPdf) log.warn("Custom page range ignored for non-PDF file type: {}", extension);
             else if (originalPageCount <= 0) log.warn("Custom page range ignored: Original page count unknown/zero for PDF '{}'.", originalColorStoredFileName);
             else log.warn("Custom page range requested but parameters invalid/missing (Start: {}, End: {}). Using all pages for '{}'.", startPage, endPage, originalColorStoredFileName);
        }

        // --- Generate Final B&W Version ---
         String bwBaseName = "bw_" + uniqueId + "_" + baseName;
         String rangeSuffix = (isPdf && "custom".equals(pageRangeType) && startPage != null && endPage != null) ? "_p" + startPage + "-" + endPage : "";
         String finalBwFileName = bwBaseName + rangeSuffix + extension;
        Path finalBwTargetPath = this.fileStorageLocation.resolve(finalBwFileName);
        String bwSourcePath = finalColorFilePath.toString();

        log.info("Attempting to generate final B&W file '{}' from source '{}'", finalBwFileName, bwSourcePath);
        String bUrl = null;

        try {
            if (finalPageCount > 0) {
                if (isPdf) {
                    convertPdfToBW(bwSourcePath, finalBwTargetPath.toString());
                } else if (isImage) {
                    convertImageToBW(bwSourcePath, finalBwTargetPath.toString());
                } else {
                    log.warn("Unsupported type for B&W conversion: extension='{}', mimeType='{}'. B&W file '{}' will not be generated.", extension, mimeType, finalBwFileName);
                    finalBwFileName = null;
                }

                if (finalBwFileName != null && Files.exists(finalBwTargetPath)) {
                     bUrl = uploadsBaseUrl + finalBwFileName;
                     log.info("Successfully generated final B&W file: {}", finalBwFileName);
                } else if (finalBwFileName != null) {
                     log.error("B&W conversion command completed but output file '{}' not found!", finalBwFileName);
                     finalBwFileName = null;
                }
            } else {
                log.warn("Skipping B&W conversion for '{}' as it has 0 pages.", finalColorFileName);
                finalBwFileName = null;
            }

        } catch (IOException | InterruptedException e) {
            log.error("Failed to generate final B&W file '{}' from '{}'", finalBwFileName, bwSourcePath, e);
            finalBwFileName = null;
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }

        String cUrl = uploadsBaseUrl + finalColorFileName;

        log.info("Returning FileInfo for input '{}': B&W='{}', Color='{}', B&W URL='{}', Color URL='{}', Pages={}, MimeType='{}'",
                 inputOriginalFileName, finalBwFileName, finalColorFileName, bUrl, cUrl, finalPageCount, mimeType);

        // Pass back original name AND original mimeType
        return new FileInfo(finalBwFileName, finalColorFileName, bUrl, cUrl, finalPageCount, inputOriginalFileName, mimeType);
    }


    private void createCustomPdf(String inputPath, String outputPath, int startPage, int endPage) throws IOException {
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new IOException("Input PDF not found: " + inputPath);
        }
        try (PDDocument document = Loader.loadPDF(inputFile)) { // Use Loader
             if (startPage < 1 || endPage > document.getNumberOfPages() || startPage > endPage) {
                 throw new IllegalArgumentException(String.format("Invalid page range %d-%d for document with %d pages", startPage, endPage, document.getNumberOfPages()));
             }

             try (PDDocument customDoc = new PDDocument()) {
                 // Page numbers in PDDocument are 0-indexed
                 for (int i = startPage - 1; i < endPage; i++) {
                     customDoc.addPage(document.getPage(i));
                 }
                  if (customDoc.getNumberOfPages() == 0) {
                      throw new IOException("Resulting custom PDF has 0 pages. Range: " + startPage + "-" + endPage);
                  }
                 customDoc.save(outputPath);
                 log.info("Successfully saved custom PDF ({}-{}) to {}", startPage, endPage, outputPath);
             }
        } catch (IOException e) {
            log.error("Error creating custom PDF from {} (range {}-{}) to {}", inputPath, startPage, endPage, outputPath, e);
            throw e;
        }
    }

    private void convertPdfToBW(String inputPath, String outputPath) throws IOException, InterruptedException {
        log.debug("Converting PDF to B&W: {} -> {}", inputPath, outputPath);
        ProcessBuilder processBuilder = new ProcessBuilder(
                "gs", "-sDEVICE=pdfwrite", "-sColorConversionStrategy=Gray",
                "-dProcessColorModel=/DeviceGray", "-dCompatibilityLevel=1.4",
                "-dDetectDuplicateImages=true", "-dNOPAUSE", "-dBATCH", "-q",
                "-o", outputPath, inputPath
        );
        executeProcess(processBuilder, "Ghostscript PDF BW conversion", 60);
    }

    private void convertImageToBW(String inputPath, String outputPath) throws IOException, InterruptedException {
         log.debug("Converting Image to B&W: {} -> {}", inputPath, outputPath);
        ProcessBuilder processBuilder = new ProcessBuilder(
                "convert", inputPath, "-colorspace", "Gray", "-strip", outputPath
        );
         executeProcess(processBuilder, "ImageMagick Image BW conversion", 30);
    }

   private void executeProcess(ProcessBuilder processBuilder, String processName, int timeoutSeconds) throws IOException, InterruptedException {
        log.info("Executing command: {} {}", processName, String.join(" ", processBuilder.command()));
        Process process = null;
        StringBuilder errorOutput = new StringBuilder(); // Capture stderr
        try {
            process = processBuilder.start();

            StreamGobbler stdOutGobbler = new StreamGobbler(process.getInputStream(), log::debug);
            // Capture stderr to the StringBuilder *and* log it
            StreamGobbler stdErrGobbler = new StreamGobbler(process.getErrorStream(), line -> {
                log.error("[{}] {}", processName, line); // Log each error line
                errorOutput.append(line).append("\n"); // Append to builder
            });
            Thread outThread = new Thread(stdOutGobbler);
            Thread errThread = new Thread(stdErrGobbler);
            outThread.start();
            errThread.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            // Wait for stream gobblers to finish after process exit or timeout
            outThread.join(5000); // Wait max 5s for stdout
            errThread.join(5000); // Wait max 5s for stderr


            if (!finished) {
                log.error("{} timed out after {} seconds. Attempting to destroy.", processName, timeoutSeconds);
                process.destroyForcibly();
                throw new IOException(processName + " timed out after " + timeoutSeconds + " seconds.");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("{} failed with exit code {}. Check logs for error output.", processName, exitCode);
                String errorDetails = errorOutput.length() > 0 ? ": " + errorOutput.toString().trim() : ". Check logs for details.";
                throw new IOException(processName + " failed with exit code " + exitCode + errorDetails);
            }
            log.info("{} completed successfully.", processName);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("{} process was interrupted.", processName, e);
             if (process != null) process.destroyForcibly();
            throw new InterruptedException(processName + " interrupted.");
        } catch (IOException e) {
             // Log specific error if command not found
             if (e.getMessage() != null && e.getMessage().contains("Cannot run program") && e.getMessage().contains("No such file or directory")) {
                 log.error("!!! {} execution failed: Command ('{}') not found or not executable in PATH. Ensure Ghostscript/ImageMagick are installed and accessible. !!!", processName, processBuilder.command().get(0));
             } else {
                 log.error("{} execution failed.", processName, e);
             }
              if (process != null) process.destroyForcibly();
             throw e;
         }
    }

    private boolean isImageExtension(String extension) {
        return ".jpg".equalsIgnoreCase(extension) || ".jpeg".equalsIgnoreCase(extension) || ".png".equalsIgnoreCase(extension);
    }

    // Helper class to consume process output streams
    private static class StreamGobbler implements Runnable {
        private final InputStream inputStream;
        private final java.util.function.Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, java.util.function.Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                     consumer.accept(line);
                }
            } catch (IOException e) {
                 // Log exception during stream reading, but don't stop the main process usually
                 log.warn("Error reading process stream: {}", e.getMessage());
            }
        }
    }
}