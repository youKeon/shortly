package com.io.shortly.click;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@EnableJpaRepositories
@SpringBootApplication
public class ClickServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClickServiceApplication.class, args);
    }
}
