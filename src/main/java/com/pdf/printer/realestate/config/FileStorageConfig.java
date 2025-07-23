
package com.pdf.printer.realestate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class FileStorageConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Expose /uploads/** URL to files on disk
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:${file.upload-dir}/");
    }
}
