package com.insightagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration — allows the Vue3 frontend (any origin during dev,
 * the production domain in prod) to call the backend API and receive SSE streams.
 *
 * <p>{@code allowCredentials(true)} is required for SSE connections that carry
 * session cookies; it must be paired with an explicit origin pattern rather than
 * the wildcard {@code "*"}.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
