package org.example.backend_practice.web;


import jakarta.validation.Valid;
import org.example.backend_practice.dto.ChatRequest;
import org.example.backend_practice.dto.ChatResponse;
import org.example.backend_practice.dto.MessageResponse;
import org.example.backend_practice.service.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/{conversationId}/chat")
    public ChatResponse chat(
            @PathVariable("conversationId") long conversationId,
            @Valid @RequestBody ChatRequest request) {

        String reply = chatService.reply(
                conversationId,
                request.message()
        );

        return new ChatResponse(reply,"real");

    }

    @GetMapping("/{conversationId}/messages")
    public List<MessageResponse> messages(
            @PathVariable("conversationId") long conversationId
    ){
        return chatService.message(conversationId);
    }
}
