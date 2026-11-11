package org.example.backend_practice.ai;

import org.example.backend_practice.dto.MessageResponse;

import java.util.List;

public interface ChatGateway {
    String reply(String message, List<MessageResponse> history);
}
