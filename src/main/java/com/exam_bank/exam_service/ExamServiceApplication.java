package com.exam_bank.exam_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ExamServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamServiceApplication.class, args);
    }

}
