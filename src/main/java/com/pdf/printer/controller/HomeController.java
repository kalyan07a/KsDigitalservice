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
	@GetMapping("/myDashboard")
	public String dashboard() {
		return "customerDashboard";
	}

	
	  @GetMapping("/print")
	  public String print() 
	  {
		  return "index"; 
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

}