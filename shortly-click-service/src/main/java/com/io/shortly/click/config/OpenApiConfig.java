package com.io.shortly.click.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI clickServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Shortly Click Analytics API")
                .version("v1")
                .description("Analytics endpoints exposing click aggregates and history.")
            );
    }

    @Bean
    public GroupedOpenApi clickServiceApi() {
        return GroupedOpenApi.builder()
            .group("click-service")
            .packagesToScan("com.io.shortly.click.api")
            .pathsToMatch("/api/**")
            .build();
    }
}
