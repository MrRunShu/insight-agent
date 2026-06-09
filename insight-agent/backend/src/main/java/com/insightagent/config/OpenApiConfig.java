package com.insightagent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI insightAgentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("InsightAgent API")
                        .description("Conversational analysis assistant - news-focused, three-tier response (chat / single-function analysis / ReAct deep analysis)")
                        .version("0.0.1")
                        .contact(new Contact().name("InsightAgent").url("https://github.com/"))
                        .license(new License().name("MIT")));
    }
}
