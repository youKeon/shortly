package com.io.shortly.url;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@EnableCaching
@EnableJpaRepositories
@SpringBootApplication
public class UrlServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlServiceApplication.class, args);
    }
}
