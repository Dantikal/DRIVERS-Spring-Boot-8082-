package com.drivers.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class UploadWebConfig implements WebMvcConfigurer {

    @Value("${drivers.uploads.returns-dir:uploads/returns}")
    private String returnsUploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get(returnsUploadDir).toAbsolutePath().normalize();
        String resourceLocation = uploadDir.toUri().toString();
        if (!resourceLocation.endsWith("/")) {
            resourceLocation = resourceLocation + "/";
        }
        registry.addResourceHandler("/uploads/returns/**")
                .addResourceLocations(resourceLocation);
    }
}
