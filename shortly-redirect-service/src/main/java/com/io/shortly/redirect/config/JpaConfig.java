package com.io.shortly.redirect.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.io.shortly.redirect.infrastructure.persistence.jpa")
public class JpaConfig {
}
