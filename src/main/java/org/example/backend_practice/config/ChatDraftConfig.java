package org.example.backend_practice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class ChatDraftConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService chatDraftScheduler() {
        return Executors.newScheduledThreadPool(
                2,
                Thread.ofPlatform().daemon(true).name("chat-draft-", 0).factory()
        );
    }
}

