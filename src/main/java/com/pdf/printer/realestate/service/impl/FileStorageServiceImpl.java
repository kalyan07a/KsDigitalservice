
package com.pdf.printer.realestate.service.impl;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.pdf.printer.realestate.exception.FileStorageException;
import com.pdf.printer.realestate.service.FileStorageService;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final Path uploadDir;

    public FileStorageServiceImpl(@Value("${file.upload-dir}") String dir) throws IOException {
        this.uploadDir = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
    }

    @Override
    public List<String> store(List<MultipartFile> files) {
        return files.stream().map(this::saveFile).collect(Collectors.toList());
    }

    private String saveFile(MultipartFile file) {
        try {
            String ext = Path.of(file.getOriginalFilename()).getFileName().toString();
            String filename = UUID.randomUUID() + "_" + ext;
            Path target = this.uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return filename;
        } catch (Exception e) {
            throw new FileStorageException("Could not store file " + file.getOriginalFilename(), e);
        }
    }
}
