
package com.pdf.printer.realestate.controller;

import com.pdf.printer.realestate.model.SaleStatus;
import com.pdf.printer.realestate.service.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class PublicController {

    private final PropertyService propertyService;

    @GetMapping("/properties")
    public String view(Model model) {
        model.addAttribute("onSale", propertyService.findByStatus(SaleStatus.ON_SALE));
        model.addAttribute("sold",   propertyService.findByStatus(SaleStatus.SOLD));
        return "listings";
    }
}
