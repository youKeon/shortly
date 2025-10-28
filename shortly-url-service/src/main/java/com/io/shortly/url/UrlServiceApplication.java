package com.io.shortly.url;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class UrlServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlServiceApplication.class, args);
    }
}
