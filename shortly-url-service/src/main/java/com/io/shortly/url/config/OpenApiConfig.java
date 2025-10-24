package com.io.shortly.url.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI urlServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Shortly URL Service API")
                .version("v1")
                .description("Manages short URL creation and metadata.")
            );
    }

    @Bean
    public GroupedOpenApi urlServiceApi() {
        return GroupedOpenApi.builder()
            .group("url-service")
            .packagesToScan("com.io.shortly.url.api")
            .pathsToMatch("/api/**")
            .build();
    }
}
