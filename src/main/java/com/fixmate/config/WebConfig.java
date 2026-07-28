package com.fixmate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // Allowed browser origins (comma-separated). Defaults to the local dev ports.
    // In production set app.cors.allowed-origins (env: APP_CORS_ALLOWED_ORIGINS)
    // to the deployed site URL, e.g. https://your-site.example.com
    @Value("${app.cors.allowed-origins:"
            + "http://localhost:5173,http://localhost:5174,http://localhost:5175,"
            + "http://localhost:5176,http://localhost:3000}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
