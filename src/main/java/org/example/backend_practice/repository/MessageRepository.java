package org.example.backend_practice.repository;

import org.example.backend_practice.dto.MessageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
public class MessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public MessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(long conversationId, String role, String content) {
        save(conversationId, role, content, "completed");
    }

    public void save(
            long conversationId, String role, String content, String status) {
        insert(conversationId, role, content, status);
    }

    // 返回新消息的数据库 ID。
    public long insert(
            long conversationId, String role, String content, String status) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO practice_messages " +
                            "(conversation_id, role, content, status) VALUES (?, ?, ?, ?)",
                    new String[]{"id"}
            );
            statement.setLong(1, conversationId);
            statement.setString(2, role);
            statement.setString(3, content);
            statement.setString(4, status);
            return statement;
        }, keys);

        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("未能取得消息 ID");
        }
        return id.longValue();
    }

    // 草稿保存只修改正文，状态仍为 generating。
    public void updateDraft(long messageId, String content) {
        int changed = jdbcTemplate.update(
                "UPDATE practice_messages SET content = ? " +
                        "WHERE id = ? AND role = 'assistant' AND status = 'generating'",
                content, messageId
        );
        if (changed != 1) {
            throw new IllegalStateException("草稿不存在或已结束");
        }
    }

    // 正常完成、停止或失败，都更新同一条助手记录。
    public void finishDraft(long messageId, String content, String status) {
        if ("generating".equals(status)) {
            throw new IllegalArgumentException("结束状态不能是 generating");
        }
        int changed = jdbcTemplate.update(
                "UPDATE practice_messages SET content = ?, status = ? " +
                        "WHERE id = ? AND role = 'assistant' AND status = 'generating'",
                content, status, messageId
        );
        if (changed != 1) {
            throw new IllegalStateException("草稿不存在或已结束");
        }
    }

    // 仅适用于本课的单实例启动恢复。
    public int recoverInterruptedDrafts() {
        return jdbcTemplate.update(
                "UPDATE practice_messages SET status = 'interrupted' " +
                        "WHERE role = 'assistant' AND status = 'generating'"
        );
    }

    public List<MessageResponse> findByConversationId(long conversationId) {
        return jdbcTemplate.query(
                "SELECT id, conversation_id, role, content, status " +
                        "FROM practice_messages WHERE conversation_id = ? ORDER BY id",
                (rs, rowNum) -> new MessageResponse(
                        rs.getLong("id"),
                        rs.getLong("conversation_id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getString("status")
                ),
                conversationId
        );
    }
}

