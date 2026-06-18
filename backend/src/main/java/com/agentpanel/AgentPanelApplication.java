package com.agentpanel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgentPanelApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentPanelApplication.class, args);
    }
}
