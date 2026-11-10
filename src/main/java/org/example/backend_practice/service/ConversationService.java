package org.example.backend_practice.service;

import org.example.backend_practice.exception.ConversationNotFoundException;
import org.example.backend_practice.dto.ConversationResponse;
import org.example.backend_practice.repository.ConversationRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConversationService {

//    //这些是保存到内存，未持久化存储
//    private final Map<Long, ConversationResponse> conversations=
//            new LinkedHashMap<>();
//
//    private  long nexId=1;
//    public synchronized ConversationResponse create(String title){
//        long id =nexId;
//        nexId++;
//        ConversationResponse conversion=
//                new ConversationResponse(id,title.strip());
//
//        conversations.put(id,conversion);
//
//        return conversion;
//    }
//
//    public synchronized List<ConversationResponse> list(){
//        return List.copyOf(conversations.values());
//    }
//
//
//    /*
//    Map 中有这个会话编号 → 检查通过，继续执行
//    Map 中没有这个编号   → 抛出异常，中止当前流程
//    */
//    public synchronized void requireExist(long conversationId){
//        if(!conversations.containsKey(conversationId)){
//            throw new ConversationNotFoundException(conversationId);
//        }
//    }


    //持久化存储（mysql）
    private final ConversationRepository conversationRepository;

    public ConversationService(
            ConversationRepository conversationRepository){
        this.conversationRepository = conversationRepository;
    }
    public ConversationResponse create(String title) {
        return conversationRepository.create(title.strip());

    }

    public List<ConversationResponse> list() {
        return conversationRepository.list();

    }

    public void requireExist(long conversationId){
        if(!conversationRepository.exists(conversationId)){
            throw new ConversationNotFoundException(conversationId);
        }
    }
}
