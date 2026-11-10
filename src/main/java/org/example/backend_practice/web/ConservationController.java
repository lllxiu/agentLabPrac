package org.example.backend_practice.web;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.backend_practice.dto.ConversationResponse;
import org.example.backend_practice.dto.CreateConversationRequest;
import org.example.backend_practice.service.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConservationController {

    private final ConversationService conversationService;

    public ConservationController(ConversationService conversationService){
        this.conversationService=conversationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse create(
            @Valid @RequestBody CreateConversationRequest request){
        return  conversationService.create(request.title());
    }

    @GetMapping
    public List<ConversationResponse> list(){
        return conversationService.list();

    }
}
