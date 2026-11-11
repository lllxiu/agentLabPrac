package org.example.backend_practice.web;

import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.example.backend_practice.dto.ChatRequest;
import org.example.backend_practice.service.ChatStreamService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/conversations")
public class ChatStreamController {
    private final ChatStreamService chatStreamService;
    public ChatStreamController(ChatStreamService chatStreamService) {
        this.chatStreamService = chatStreamService;
    }
    @PostMapping(
            value = "/{conversationId}/chat/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter stream(
            @PathVariable("conversationId") long conversationId,
            @Valid @RequestBody ChatRequest request) {

        return chatStreamService.stream(
                conversationId,
                request.message()
        );
    }

}
