package com.ofd.converter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OfdConverterApplication {
    public static void main(String[] args) {
        SpringApplication.run(OfdConverterApplication.class, args);
    }
}
