package com.pdf.printer.controller;

import com.pdf.printer.dto.FileInfo;
import com.pdf.printer.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value; // Import Value

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/print")
public class FileUploadController implements WebMvcConfigurer { // Implement WebMvcConfigurer

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    private final FileStorageService fileStorageService;

    @Value("${file.upload-dir}") // Inject uploadDir here as well for resource handler
    private String uploadDir;


    @Autowired
    public FileUploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/")
    public String index() {
        log.info("Serving index page.");
        return "index";
    }

    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadAndProcessFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("uniqueId") String uniqueId,
            @RequestParam(value = "pageRangeType", defaultValue = "all") String pageRangeType,
            @RequestParam(value = "startPage", required = false) Integer startPage,
            @RequestParam(value = "endPage", required = false) Integer endPage
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("uniqueId", uniqueId);

        if (file.isEmpty()) {
            log.warn("Upload request received for uniqueId '{}' but file is empty.", uniqueId);
            response.put("error", "Please select a file to upload.");
            response.put("status", "error");
            return ResponseEntity.badRequest().body(response);
        }

        String originalFilename = file.getOriginalFilename();
        log.info("Received upload request for uniqueId '{}': File='{}', Size={}, Type={}, Range='{}', Start={}, End={}",
                 uniqueId, originalFilename, file.getSize(), file.getContentType(), pageRangeType, startPage, endPage);

        try {
            FileInfo processedFileInfo = fileStorageService.storeFile(file, pageRangeType, startPage, endPage);
            log.info("File processing complete for uniqueId '{}'. Result: {}", uniqueId, processedFileInfo);

            response.put("originalFileName", processedFileInfo.getOriginalFileName());
            response.put("url", processedFileInfo.getUrl());
            response.put("c_url", processedFileInfo.getC_url());
            response.put("fileName", processedFileInfo.getC_fileName());
            response.put("bwFileName", processedFileInfo.getB_fileName());
            response.put("pageCount", processedFileInfo.getPageCount());
            response.put("mimeType", processedFileInfo.getMimeType()); // Pass mimeType back
            response.put("status", "processed");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid arguments during file processing for uniqueId '{}', File '{}': {}", uniqueId, originalFilename, e.getMessage());
            response.put("error", e.getMessage());
            response.put("status", "error");
            return ResponseEntity.badRequest().body(response);
        } catch (IOException e) {
            log.error("File processing failed for uniqueId '{}', File '{}': {}", uniqueId, originalFilename, e.getMessage(), e);
            response.put("error", "File processing failed: " + e.getMessage());
            response.put("status", "error");
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
            log.error("Unexpected error during file processing for uniqueId '{}', File '{}'", uniqueId, originalFilename, e);
            response.put("error", "An unexpected server error occurred during processing.");
            response.put("status", "error");
            return ResponseEntity.internalServerError().body(response);
        }
    }

     // --- Resource Handler for serving uploaded files ---
     // This is crucial for the preview URLs (/uploads/...) to work
     @Override
     public void addResourceHandlers(ResourceHandlerRegistry registry) {
         Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
         String uploadPathUrl = uploadPath.toUri().toString();
         log.info("Configuring resource handler for '/uploads/**' mapping to '{}'", uploadPathUrl);

         registry.addResourceHandler("/uploads/**")
                 .addResourceLocations(uploadPathUrl); // Use file URI
     }
}