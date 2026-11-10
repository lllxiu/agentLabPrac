package org.example.backend_practice.repository;

import org.example.backend_practice.dto.MessageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public MessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(long conversationId, String role, String content) {
        String sql =
                "INSERT INTO practice_messages " +
                        "(conversation_id, role, content) VALUES (?, ?, ?)";

        jdbcTemplate.update(
                sql,
                conversationId,
                role,
                content
        );
    }

    public List<MessageResponse> findByConversationId(
            long conversationId) {

        String sql =
                "SELECT id, conversation_id, role, content " +
                        "FROM practice_messages " +
                        "WHERE conversation_id = ? " +
                        "ORDER BY id";

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new MessageResponse(
                        rs.getLong("id"),
                        rs.getLong("conversation_id"),
                        rs.getString("role"),
                        rs.getString("content")
                ),
                conversationId
        );
    }
}