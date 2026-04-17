package com.store.repair;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RepairBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepairBackendApplication.class, args);
    }
}
