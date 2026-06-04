package com.chatops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChatOpsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatOpsApplication.class, args);
    }
}
