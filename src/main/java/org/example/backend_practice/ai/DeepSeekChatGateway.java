package org.example.backend_practice.ai;

import org.example.backend_practice.dto.MessageResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
public class DeepSeekChatGateway implements ChatGateway {
    private final ChatClient chatClient;

    public DeepSeekChatGateway(ChatClient.Builder builder) {
        this.chatClient=builder.build();
    }


    @Override
    public String reply(
            String message,
            List<MessageResponse> history
    ){

        String reply=chatClient.prompt()
                .system("你是一个有耐心的编程助教，请结合对话上下文回答。")
                .messages(buildMessages(message,history))
                .call()
                .content();

        if(reply==null||reply.isBlank()){
            throw new IllegalStateException("模型未有效回答。");
        }
        return reply;
    }
    @Override
    public Flux<String> stream(
            String message,
            List<MessageResponse> history){
        return chatClient.prompt()
                .system("你是一个有耐心的编程助教，请结合对话上下文回答。")
                .messages(buildMessages(message,history))
                .stream()
                .content();
    }

    private List<Message> buildMessages(String message, List<MessageResponse> history){
        List<Message> input=new ArrayList<>();

        int start =Math.max(0,history.size()-20);

        for(int i=start;i<history.size();i++){
            MessageResponse item=history.get(i);

            if("user".equals(item.role())){
                input.add(new UserMessage(item.content()));
            }else if("assistant".equals(item.role())&& "completed".equals(item.status())){
                input.add(new AssistantMessage(item.content()));
            }
        }

        input.add(new UserMessage(message));
        return input;
    }
}
