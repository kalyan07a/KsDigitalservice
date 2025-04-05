package com.pdf.printer.controller;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    // Serves the main page structure which includes the tabs
    @GetMapping("/")
    public String homePage() {
        return "home"; // Renders src/main/resources/templates/index.html
    }

    // API endpoint called by the "Print" tab/button's JavaScript
    // This could be in another @Controller class if you prefer
    @GetMapping("/print")
 //   @ResponseBody // Important: Return data directly, not a view name
    public String print() {
        return "index";
    }
    @GetMapping("/google0062e0de736cb797.html")
    public String owner()
    {
    	return "google0062e0de736cb797";
    }
    
    
    
    @GetMapping("/terms")
    public String terms() {
        return "terms"; 
    }
    
    @GetMapping("/privacy")
    public String privacy() {
        return "privacy"; 
    }
    
    @GetMapping("/cancellation")
    public String cancellation() {
        return "cancellation"; 
    }
    
    @GetMapping("/contact")
    public String contact() {
        return "contact"; 
    }
    
    
}