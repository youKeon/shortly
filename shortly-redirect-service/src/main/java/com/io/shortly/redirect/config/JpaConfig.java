package com.io.shortly.redirect.mvc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@Profile("mvc")
@EnableJpaRepositories(basePackages = "com.io.shortly.redirect.mvc.infrastructure.persistence.jpa")
public class JpaConfig {
}
