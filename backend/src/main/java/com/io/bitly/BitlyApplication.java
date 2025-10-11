package com.io.bitly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class BitlyApplication {

	public static void main(String[] args) {
		SpringApplication.run(BitlyApplication.class, args);
	}

}
