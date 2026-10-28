package com.sgn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SgnApp {
    public static void main(String[] args) {
        SpringApplication.run(SgnApp.class, args);
    }
}
