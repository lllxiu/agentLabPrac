package org.example.backend_practice.dto;

public record MessageResponse(
        //消息编号，所属会话编号，角色，正文
        long id,
        long conversationId,
        String role,
        String content,
        String status

) {
}
