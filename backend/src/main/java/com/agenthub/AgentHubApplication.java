package com.agenthub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.agenthub.repository")
public class AgentHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentHubApplication.class, args);
    }
}
