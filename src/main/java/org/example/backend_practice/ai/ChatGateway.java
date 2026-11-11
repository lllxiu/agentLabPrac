package org.example.backend_practice.ai;

import org.example.backend_practice.dto.MessageResponse;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ChatGateway {
    String reply(String message, List<MessageResponse> history);
    Flux<String> stream(String message, List<MessageResponse> history);
}
