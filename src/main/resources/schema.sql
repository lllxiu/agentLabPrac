CREATE TABLE IF NOT EXISTS practice_conversations(
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(80) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS practice_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'completed',

    PRIMARY KEY (id),

    INDEX idx_practice_messages_conversation (conversation_id, id),

    CONSTRAINT fk_practice_messages_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES practice_conversations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;