
package com.pdf.printer.realestate.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    List<String> store(List<MultipartFile> files);
}
