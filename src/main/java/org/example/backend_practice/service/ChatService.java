package org.example.backend_practice.service;

import org.example.backend_practice.ai.ChatGateway;
import org.example.backend_practice.dto.MessageResponse;
import org.example.backend_practice.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


//本地模拟
//@Service
//public class ChatService {
//
//    private final ConversationService conversationService;
//
//    private final Map<Long,List<MessageResponse>> histories =new HashMap<>();
//
//    private long nextMessageId=1;
//
//    public ChatService(ConversationService conversationService){
//        this.conversationService = conversationService;
//    }
//
//    public synchronized String reply(long conversationId,String message){
//
//        //检查是否存在，再获取消息列表
//        conversationService.requireExist(conversationId);
//        List<MessageResponse> history = histories.get(conversationId);
//
//        //首次聊天，为该会话创建空列表
//        if(history == null){
//            history = new ArrayList<>();
//            histories.put(conversationId,history);
//        }
//
//        //保存用户消息
//        history.add(new MessageResponse(
//                nextMessageId++,
//                conversationId,
//                "user",
//                message
//        ));
//
//        //模拟回复
//        String reply="收到"+message+"这是本地的模拟回复";
//        history.add(new MessageResponse(
//                nextMessageId++,
//                conversationId,
//                "assistant",
//                reply
//        ));
//        return reply;
//    }
//
//    public synchronized List<MessageResponse> message(long conversationId){
//        conversationService.requireExist(conversationId);
//        List<MessageResponse> history = histories.get(conversationId);
//        if(history == null){
//            return List.of();
//        }
//        return List.copyOf(history);
//    }
//}


//数据持久化mysql
@Service
public class ChatService {

    private final ConversationService conversationService;
    private final MessageRepository messageRepository;
    private final ChatGateway chatGateway;
    private final TransactionTemplate transactions;

    public ChatService(
            ConversationService conversationService,
            MessageRepository messageRepository,
            ChatGateway chatGateway,
            PlatformTransactionManager transactionManager
    ) {

        this.conversationService = conversationService;
        this.messageRepository = messageRepository;
        this.chatGateway=chatGateway;
        this.transactions=new TransactionTemplate(transactionManager);
    }

    public String reply(long conversationId, String message) {

        // 1. 确认会话存在。
        conversationService.requireExist(conversationId);

        // 2. 查询之前历史
        List<MessageResponse> history=
                messageRepository.findByConversationId(conversationId);

        // 3.等待模型回复。
        String reply = chatGateway.reply(message,history);

        // 4. 在同一事务中保存问题和回答。
        transactions.executeWithoutResult(status->{
            messageRepository.save(conversationId,"user",message);
            messageRepository.save(conversationId,"assistant",reply);
        });

        return reply;
    }

    public List<MessageResponse> message(long conversationId) {
        conversationService.requireExist(conversationId);

        return messageRepository.findByConversationId(conversationId);
    }
}