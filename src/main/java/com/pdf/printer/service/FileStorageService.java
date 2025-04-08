
package com.pdf.printer.service;

import com.pdf.printer.dto.FileInfo;
import org.apache.pdfbox.multipdf.Splitter; // For splitting PDF
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils; // For filename cleaning
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private final Path fileStorageLocation;
    private final String uploadsBaseUrl = "/uploads/"; // Base URL prefix

    public FileStorageService(@Value("${file.upload-dir}") String uploadDir) throws IOException {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        log.info("File storage location initialized at: {}", this.fileStorageLocation);
        if (!Files.exists(this.fileStorageLocation)) {
            try {
                Files.createDirectories(this.fileStorageLocation);
                log.info("Created upload directory: {}", this.fileStorageLocation);
            } catch (IOException ex) {
                log.error("Could not create upload directory: {}", this.fileStorageLocation, ex);
                throw new IOException("Could not create upload directory: " + this.fileStorageLocation, ex);
            }
        }
    }

    // --- Modified storeFile Method ---
    public FileInfo storeFile(MultipartFile file, String pageRangeType, Integer startPage, Integer endPage) throws IOException, IllegalArgumentException {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename()); // Clean path
        if (originalFileName == null || originalFileName.isEmpty()) {
            originalFileName = "upload_" + UUID.randomUUID(); // Generate name if missing
        }
        originalFileName = originalFileName.replace('-', '_').replaceAll("[^a-zA-Z0-9._-]", ""); // Sanitize

        String extension = "";
        String baseName = originalFileName;
        int lastDotIndex = originalFileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = originalFileName.substring(lastDotIndex).toLowerCase(); // Ensure lowercase extension
            baseName = originalFileName.substring(0, lastDotIndex);
        }

        // --- Store the Original Color File First ---
        String uniqueId = UUID.randomUUID().toString().substring(0, 8); // Shorter UUID part
        String originalColorStoredFileName = baseName + "_" + uniqueId + extension;
        //String originalColorStoredFileName = baseName + extension;
        Path originalColorTargetPath = this.fileStorageLocation.resolve(originalColorStoredFileName);
        log.info("Storing original file as: {}", originalColorStoredFileName);
        try {
            Files.copy(file.getInputStream(), originalColorTargetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.error("Could not store original file {}", originalColorStoredFileName, ex);
            throw new IOException("Could not store file " + originalColorStoredFileName + ". Please try again!", ex);
        }

        // --- Initial Variables ---
        Path finalColorFilePath = originalColorTargetPath;
        String finalColorFileName = originalColorStoredFileName;
        int finalPageCount = 0;
        boolean isPdf = ".pdf".equals(extension);

        // --- Calculate Original Page Count ---
        int originalPageCount = 0;
        if (isPdf) {
            try (PDDocument document = PDDocument.load(originalColorTargetPath.toFile())) {
                originalPageCount = document.getNumberOfPages();
                log.info("Original PDF page count: {}", originalPageCount);
            } catch (IOException e) {
                log.error("Error reading original PDF for page count: {}", originalColorStoredFileName, e);
                // Allow proceeding, but custom range won't work without page count
            }
        } else if (isImage(extension)) {
            originalPageCount = 1; // Images are always 1 page in this context
            log.info("Image detected, page count set to 1");
        } else {
             log.warn("Unsupported file type for page count: {}", extension);
             // Cannot process page ranges for unsupported types
             pageRangeType = "all"; // Force to 'all' if type is unknown/unsupported for ranges
        }

        finalPageCount = originalPageCount; // Default to original page count

        // --- Handle Custom Page Range (Only for PDFs with known page count) ---
        if (isPdf && "custom".equals(pageRangeType) && startPage != null && endPage != null && originalPageCount > 0) {
            log.info("Processing custom page range: {} - {}", startPage, endPage);

            // Validate range
            if (startPage < 1 || endPage < startPage || endPage > originalPageCount) {
                log.error("Invalid page range requested: {}-{} for PDF with {} pages.", startPage, endPage, originalPageCount);
                throw new IllegalArgumentException("Invalid page range. Start must be >= 1, End must be >= Start, and End must not exceed total pages (" + originalPageCount + ").");
            }

            // Create custom PDF
            String customColorFileName = "custom_" + baseName + "_" + uniqueId + "_p" + startPage + "-" + endPage + extension;
            //String customColorFileName = "custom_" + baseName + extension;
            Path customColorTargetPath = this.fileStorageLocation.resolve(customColorFileName);
            try {
                createCustomPdf(originalColorTargetPath.toString(), customColorTargetPath.toString(), startPage, endPage);
                finalColorFilePath = customColorTargetPath; // Update final path
                finalColorFileName = customColorFileName; // Update final name
                // Recalculate page count for the new file
                try (PDDocument customDoc = PDDocument.load(customColorTargetPath.toFile())) {
                    finalPageCount = customDoc.getNumberOfPages();
                }
                log.info("Created custom color PDF: {} with {} pages", customColorFileName, finalPageCount);
            } catch (IOException e) {
                log.error("Failed to create custom PDF from {} to {}", originalColorStoredFileName, customColorFileName, e);
                // Fallback to original file if custom creation fails? Or throw error? Let's throw.
                throw new IOException("Failed to create custom page range PDF.", e);
            }
        } else if ("custom".equals(pageRangeType)) {
            if (!isPdf) {
                 log.warn("Custom page range ignored for non-PDF file type: {}", extension);
            } else if (originalPageCount <= 0) {
                log.warn("Custom page range ignored because original page count could not be determined for PDF: {}", originalColorStoredFileName);
            } else {
                log.warn("Custom page range requested but parameters invalid or missing (Start: {}, End: {}). Using all pages.", startPage, endPage);
            }
            // If custom was requested but couldn't be processed, ensure we use 'all pages' logic implicitly
        }


        // --- Generate Final B&W Version (Based on the finalColorFilePath) ---
        String finalBwFileName = "bw_" + finalColorFileName; // Name based on final color name
        Path finalBwTargetPath = this.fileStorageLocation.resolve(finalBwFileName);
        log.info("Generating final B&W file: {}", finalBwFileName);
        try {
            if (isPdf) {
                convertPdfToBW(finalColorFilePath.toString(), finalBwTargetPath.toString());
            } else if (isImage(extension)) {
                convertImageToBW(finalColorFilePath.toString(), finalBwTargetPath.toString());
            } else {
                // For unsupported types, maybe just copy the color file? Or leave B&W empty?
                // Let's just copy the color file for now, meaning B&W URL will point to color.
                Files.copy(finalColorFilePath, finalBwTargetPath, StandardCopyOption.REPLACE_EXISTING);
                log.warn("Unsupported type for B&W conversion: {}. Copied color file as B&W placeholder.", extension);
            }
             log.info("Successfully generated/handled final B&W file: {}", finalBwFileName);
        } catch (IOException | InterruptedException e) {
            log.error("Failed to generate final B&W file: {}", finalBwFileName, e);
            // If BW fails, should we still return info? Maybe return null for BW fields?
            // Let's return null for BW file/URL if conversion fails.
            finalBwFileName = null; // Indicate failure
             if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }

        // --- Construct URLs ---
        String bUrl = (finalBwFileName != null) ? uploadsBaseUrl + finalBwFileName : null;
        String cUrl = uploadsBaseUrl + finalColorFileName;

        // --- Return FileInfo with Final Details ---
        log.info("Returning FileInfo: B&W='{}', Color='{}', B&W URL='{}', Color URL='{}', Pages={}",
                 finalBwFileName, finalColorFileName, bUrl, cUrl, finalPageCount);
        return new FileInfo(finalBwFileName, finalColorFileName, bUrl, cUrl, finalPageCount);
    }


    // --- New Method to Create Custom PDF using PDFBox ---
    private void createCustomPdf(String inputPath, String outputPath, int startPage, int endPage) throws IOException {
        File inputFile = new File(inputPath);
        try (PDDocument document = PDDocument.load(inputFile)) {
            Splitter splitter = new Splitter();
            // PDFBox pages are 0-indexed, user input is 1-indexed
            splitter.setStartPage(startPage);
            splitter.setEndPage(endPage);
            splitter.setSplitAtPage(endPage - startPage + 1); // Split after the desired number of pages

            List<PDDocument> splitDocuments = splitter.split(document);

            if (!splitDocuments.isEmpty()) {
                try (PDDocument customDoc = splitDocuments.get(0)) {
                    customDoc.save(outputPath);
                    log.info("Successfully saved custom PDF to {}", outputPath);
                }
                // Close any other potentially split documents (though should only be one here)
                for (int i = 1; i < splitDocuments.size(); i++) {
                    splitDocuments.get(i).close();
                }
            } else {
                log.error("PDFBox Splitter returned no documents for range {}-{} in file {}", startPage, endPage, inputPath);
                throw new IOException("Failed to split PDF document.");
            }
        } catch (IOException e) {
            log.error("Error creating custom PDF from {} (range {}-{}) to {}", inputPath, startPage, endPage, outputPath, e);
            throw e;
        }
    }

    // --- Existing Conversion Methods (Keep as is) ---
    private void convertPdfToBW(String inputPath, String outputPath) throws IOException, InterruptedException {
        log.debug("Converting PDF to B&W: {} -> {}", inputPath, outputPath);
        ProcessBuilder processBuilder = new ProcessBuilder(
                "gs", "-q", "-sDEVICE=pdfwrite",
                "-dColorConversionStrategy=/Gray", "-dProcessColorModel=/DeviceGray",
                "-o", outputPath, inputPath
        );
        executeProcess(processBuilder, "Ghostscript PDF BW conversion");
    }

    private void convertImageToBW(String inputPath, String outputPath) throws IOException, InterruptedException {
         log.debug("Converting Image to B&W: {} -> {}", inputPath, outputPath);
        ProcessBuilder processBuilder = new ProcessBuilder(
                "convert", inputPath, "-colorspace", "Gray", outputPath
        );
         executeProcess(processBuilder, "ImageMagick Image BW conversion");
    }

    private void executeProcess(ProcessBuilder processBuilder, String processName) throws IOException, InterruptedException {
         try {
            Process process = processBuilder.start();
            // Add input/error stream handling for better debugging if needed
             // process.getInputStream().transferTo(System.out); // Example
             // process.getErrorStream().transferTo(System.err); // Example
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("{} failed with exit code {}", processName, exitCode);
                throw new IOException(processName + " failed with exit code " + exitCode);
            }
             log.debug("{} completed successfully.", processName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("{} interrupted", processName, e);
            throw new IOException(processName + " interrupted", e);
        } catch (IOException e) {
             log.error("{} execution failed", processName, e);
             throw e; // Re-throw IO Exception
         }
    }

    private boolean isImage(String extension) {
        return ".jpg".equals(extension) || ".jpeg".equals(extension) || ".png".equals(extension);
    }
}