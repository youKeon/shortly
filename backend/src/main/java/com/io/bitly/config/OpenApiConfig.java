package com.io.bitly.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
    info = @Info(
        title = "Bitly API",
        version = "v1",
        description = "URL shortening API documentation"
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Local")
    }
)
@Configuration
public class OpenApiConfig {
}
