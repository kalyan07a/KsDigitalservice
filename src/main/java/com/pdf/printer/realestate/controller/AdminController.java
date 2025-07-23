
package com.pdf.printer.realestate.controller;

import com.pdf.printer.realestate.dto.PropertyDto;
import com.pdf.printer.realestate.model.SaleStatus;
import com.pdf.printer.realestate.service.PropertyService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

@Controller
@RequestMapping("/admin/properties")
@RequiredArgsConstructor
public class AdminController {
 
    private final PropertyService propertyService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("properties", propertyService.findAll());
        model.addAttribute("propertyDto", new PropertyDto());
        return "admin";
    }

    @PostMapping
    public String create(@ModelAttribute @Validated PropertyDto dto,
                         @RequestParam("images") MultipartFile[] images) {
        // Convert array to list and filter out empty files
        dto.setImages(Arrays.stream(images)
                .filter(file -> !file.isEmpty())
                .toList());
        
        propertyService.create(dto);
        return "redirect:/admin/properties";
    }

    @PatchMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam SaleStatus status) {
        propertyService.updateStatus(id, status);
        return "redirect:/admin/properties";
    }
    @DeleteMapping("{id}")
    public String delete(@PathVariable Long id) {
        propertyService.deleteById(id);
        return "redirect:/admin/properties";
    }
}
