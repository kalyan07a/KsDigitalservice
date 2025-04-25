package com.pdf.printer.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class MultipleUsersController {
	@GetMapping("/print/{id}")
    public String getItem(@PathVariable("id") Long id, Model model) {
        model.addAttribute("id", id);
        return "index"; 
    }

}
