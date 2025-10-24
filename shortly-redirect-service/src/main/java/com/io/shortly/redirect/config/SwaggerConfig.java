package com.io.shortly.redirect.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi webfluxApi() {
        return GroupedOpenApi.builder()
            .group("webflux-api")
            .pathsToMatch("/api/**")
            .build();
    }

    @Bean
    public OpenAPI redirectServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Shortly Redirect Service API")
                .version("v1")
                .description("Reactive redirect endpoints for resolving short codes.")
            );
    }
}
