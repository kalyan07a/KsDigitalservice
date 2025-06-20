package com.pdf.printer.controller;


import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.pdf.printer.service.PhotoToPdfService;

import jakarta.servlet.http.HttpServletResponse;

@Controller
public class PhotoToPdfController {

    @Autowired
    private PhotoToPdfService pdfService;
    
    @GetMapping("/shortcut")
    public String photo()
    {
    	return "page1";
    }

    @GetMapping("/passport_photo")
    public String index(Model model) {
        return "photo";
    }
    @GetMapping("/autofill")
    public String autofill() {
        return "autofill";
    }
    

    @PostMapping("/generate-pdf")
    public void generatePdf(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam("copies") int copies,
            HttpServletResponse response) throws IOException {
        
        // Set response headers
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=photos.pdf");
        
        // Generate and stream PDF
        pdfService.generatePdf(photo, copies, response.getOutputStream());
    }
}