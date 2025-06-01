package com.pdf.printer.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class HomeController {

	private static final Logger log = LoggerFactory.getLogger(HomeController.class);

	@GetMapping("/")
	public String homePage() {
		return "home";
	}
	
	@GetMapping("/print/{id}")
    public String getItem(@PathVariable("id") Long id, Model model) {
		log.info("id is "+id);
		
		if(id==null)
			return "please scan the QR code or visit the website";
        
        String printerId=String.valueOf(id);
        Printer.PrinterDetails details = Printer.getDetailsById(printerId);
        model.addAttribute("id", id);
        model.addAttribute("name", details.getName());
		model.addAttribute("range1", details.getRange1());
		model.addAttribute("range2", details.getRange2());
		model.addAttribute("b_cost_range1", details.getB_cost_range1());
		model.addAttribute("b_cost_range2", details.getB_cost_range2());
		model.addAttribute("b_cost_range3", details.getB_cost_range3());
		model.addAttribute("c_cost_range1", details.getC_cost_range1());
		model.addAttribute("c_cost_range2", details.getC_cost_range2());
		model.addAttribute("c_cost_range3", details.getC_cost_range3());
		
        log.info("model"+model.toString());
        return "index"; 
    } 
	@GetMapping("/google0062e0de736cb797.html")
	public String crawl()
	{
		return "google0062e0de736cb797";
	}
	
	@GetMapping("/myDashboard")
	public String dashboard() {
		return "customerDashboard";
	}

	
	  @GetMapping("/print")
	  public String print() 
	  {
		  return "index"; 
	  }
	  
	  @GetMapping("/robots.txt")
	  public String page()
	  {
		  return "robots.txt";
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

	@GetMapping("/about")
	public String aboutUs() {
		return "about";
	}

	@GetMapping("/shipping")
	public String shippig() {
		return "shipping";
	}

	@GetMapping("/foodLicense")
	public String foodLicense()
	{
		return "foodLicense";
	}
	@GetMapping("/tradeLicense")
	public String tradeLicense()
	{
		return "tradeLicense";
	}
	@GetMapping("/labourLicense")
	public String labourLicense()
	{
		return "labourLicense";
	}
	@GetMapping("/msme")
	public String msme()
	{
		return "msme";
	}
	@GetMapping("/passport")
	public String passport()
	{
		return "passport";
	}
	@GetMapping("/pancard")
	public String pancard()
	{
		return "pancard";
	}
}