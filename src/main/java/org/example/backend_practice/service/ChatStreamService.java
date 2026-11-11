package org.example.backend_practice.service;

import org.example.backend_practice.ai.ChatGateway;
import org.example.backend_practice.dto.MessageResponse;
import org.example.backend_practice.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class ChatStreamService {
    private static final Logger log =
            LoggerFactory.getLogger(ChatStreamService.class);

    private final ConversationService conversationService;
    private final MessageRepository messageRepository;
    private final ChatGateway chatGateway;
    private final TransactionTemplate transactions;

    public ChatStreamService(
            ConversationService conversationService,
            MessageRepository messageRepository,
            ChatGateway chatGateway,
            PlatformTransactionManager transactionManager) {

        this.conversationService = conversationService;
        this.messageRepository = messageRepository;
        this.chatGateway = chatGateway;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public SseEmitter stream(Long conversationId,String message){
        //校验会话，读取历史
        conversationService.requireExist(conversationId);

        List<MessageResponse> history = messageRepository.findByConversationId(conversationId);

        SseEmitter emitter = new SseEmitter(120_000L);

        Thread worker = Thread.ofVirtual().unstarted(
                () -> generate(emitter, conversationId, message, history)
        );

        emitter.onTimeout(() -> {
            worker.interrupt();
            emitter.complete();
        });

        emitter.onError(error -> worker.interrupt());
        emitter.onCompletion(worker::interrupt);

        worker.start();
        return emitter;

    }


    private void generate(
            SseEmitter emitter,
            long conversationId,
            String message,
            List<MessageResponse> history
    ){
        StringBuilder answer =new StringBuilder();

        try{
            // 2. 在工作线程中逐段读取并发送。
            try (Stream<String> chunks =
                         chatGateway.stream(message, history).toStream(1)) {

                Iterator<String> iterator = chunks.iterator();

                while (iterator.hasNext()) {
                    String chunk = iterator.next();

                    answer.append(chunk);

                    send(emitter, "delta", Map.of("content", chunk));
                }
            }
            if(Thread.currentThread().isInterrupted()){
                return;
            }

            String reply=answer.toString();
            if(reply.isBlank()){
                throw new IllegalStateException("模型未返回有效回答");
            }
            // 3. 模型正常结束后，才开始数据库事务。
            transactions.executeWithoutResult(status -> {
                messageRepository.save(conversationId, "user", message);
                messageRepository.save(conversationId, "assistant", reply);
            });
            // 数据库提交成功后，才发送完成事件。
            send(emitter, "done", Map.of("status", "completed"));
            emitter.complete();

        } catch (IOException ex) {
            log.debug("SSE 发送失败，连接可能已断开", ex);

        } catch (RuntimeException ex) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            log.warn("流式聊天失败，conversationId={}", conversationId, ex);

            try {
                send(emitter, "error", Map.of(
                        "code", "CHAT_FAILED",
                        "message", "本轮聊天未能正常完成，请查询历史后重试。"
                ));

                emitter.complete();

            } catch (IOException sendError) {
                log.debug("无法发送 SSE 错误事件", sendError);
            }
        }
    }

    private void send(
            SseEmitter emitter,
            String event,
            Map<String,String> data) throws IOException{

        emitter.send(
                SseEmitter.event()
                        .name(event)
                        .data(data, MediaType.APPLICATION_JSON)
        );
    }
}
