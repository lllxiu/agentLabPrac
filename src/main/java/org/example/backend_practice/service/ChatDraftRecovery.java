package org.example.backend_practice.service;

import jakarta.annotation.PostConstruct;
import org.example.backend_practice.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

@Component
@DependsOnDatabaseInitialization
public class ChatDraftRecovery {

    private static final Logger log =
            LoggerFactory.getLogger(ChatDraftRecovery.class);

    private final MessageRepository messageRepository;

    public ChatDraftRecovery(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @PostConstruct
    public void recover() {
        int count = messageRepository.recoverInterruptedDrafts();
        if (count > 0) {
            log.info("已将 {} 条遗留草稿标记为 interrupted", count);
        }
    }
}


