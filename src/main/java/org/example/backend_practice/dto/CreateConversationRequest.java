package org.example.backend_practice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @NotBlank(message = "会话标题不能为空")
        @Size(max=80,message ="会话长度不能超过80")
        String title
) {
}
