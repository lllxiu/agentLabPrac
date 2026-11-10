package org.example.backend_practice.service;

import org.example.backend_practice.dto.ConversationResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConversationService {

    private final Map<Long, ConversationResponse> conversations=
            new LinkedHashMap<>();

    private  long nexId=1;
    public synchronized ConversationResponse create(String title){
        long id =nexId;
        nexId++;
        ConversationResponse conversion=
                new ConversationResponse(id,title.strip());

        conversations.put(id,conversion);

        return conversion;
    }

    public synchronized List<ConversationResponse> list(){
        return List.copyOf(conversations.values());
    }
}
