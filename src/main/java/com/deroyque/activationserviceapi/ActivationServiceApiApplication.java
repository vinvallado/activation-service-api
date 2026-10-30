package com.deroyque.activationserviceapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ActivationServiceApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ActivationServiceApiApplication.class, args);
    }
}
