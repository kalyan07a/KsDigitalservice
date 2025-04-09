package com.pdf.printer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource; // <--- Import this

@SpringBootTest
@TestPropertySource(locations = "classpath:application.properties") 
class PrinterApplicationTests {

	@Test
	void contextLoads() {
	}

}
