package com.pdf.printer.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.pdf.printer.dto.FileInfo;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;

    public FileStorageService(@Value("${file.upload-dir}") String uploadDir) throws IOException {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(this.fileStorageLocation)) {
            Files.createDirectories(this.fileStorageLocation);
        }
    }

    public FileInfo storeFile(MultipartFile file) throws IOException {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            originalFileName = "file";
        }
        originalFileName = originalFileName.replace('-', ' ');
        String extension = "";
        int pageCount = 0;

        int lastDotIndex = originalFileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = originalFileName.substring(lastDotIndex);
            originalFileName = originalFileName.substring(0, lastDotIndex);
        }

        String uniqueId = UUID.randomUUID().toString();
        String colorFileName = "c_"+originalFileName + extension;
        String bwFileName = "b_"+originalFileName + extension;

        Path colorTargetLocation = this.fileStorageLocation.resolve(colorFileName);
        Files.copy(file.getInputStream(), colorTargetLocation, StandardCopyOption.REPLACE_EXISTING);

        if (extension.equalsIgnoreCase(".pdf")) {
            convertPdfToBW(colorTargetLocation.toString(), this.fileStorageLocation.resolve(bwFileName).toString());
        } else if (extension.equalsIgnoreCase(".jpg") || extension.equalsIgnoreCase(".jpeg") || extension.equalsIgnoreCase(".png")) {
            convertImageToBW(colorTargetLocation.toString(), this.fileStorageLocation.resolve(bwFileName).toString());
        }

        if (extension.equalsIgnoreCase(".pdf")) {
            File pdfFile = colorTargetLocation.toFile();
            try (PDDocument document = PDDocument.load(pdfFile)) {
                pageCount = document.getNumberOfPages();
            }
        } else if (extension.equalsIgnoreCase(".jpg") || extension.equalsIgnoreCase(".jpeg") || extension.equalsIgnoreCase(".png")) {
            pageCount = 1;
        }

        return new FileInfo(bwFileName,colorFileName, "/uploads/" + colorFileName, pageCount);
    }

    private void convertPdfToBW(String inputPath, String outputPath) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "gs",
                "-q",
                "-sDEVICE=pdfwrite",
                "-dColorConversionStrategy=/Gray",
                "-dProcessColorModel=/DeviceGray",
                "-o", outputPath,
                inputPath
        );
        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Ghostscript conversion failed with exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Conversion interrupted", e);
        }
    }

    private void convertImageToBW(String inputPath, String outputPath) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "convert",
                inputPath,
                "-colorspace", "Gray",
                outputPath
        );
        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("ImageMagick conversion failed with exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Conversion interrupted", e);
        }
    }
}