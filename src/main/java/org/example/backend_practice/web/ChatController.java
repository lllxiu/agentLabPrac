package org.example.backend_practice.web;


import jakarta.validation.Valid;
import org.example.backend_practice.dto.ChatRequest;
import org.example.backend_practice.dto.ChatResponse;
import org.example.backend_practice.service.ChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String reply=chatService.reply(request.message());
        return new ChatResponse(reply,"demo");

    }
}
