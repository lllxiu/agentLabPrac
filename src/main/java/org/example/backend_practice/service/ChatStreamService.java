package org.example.backend_practice.service;

import org.example.backend_practice.ai.ChatGateway;
import org.example.backend_practice.dto.MessageResponse;
import org.example.backend_practice.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
public class ChatStreamService {

    private static final Logger log =
            LoggerFactory.getLogger(ChatStreamService.class);

    private final ConversationService conversationService;
    private final MessageRepository messageRepository;
    private final ChatGateway chatGateway;
    private final TransactionTemplate transactions;

    // 记录本进程中每个会话正在运行的流式请求。
    private final ConcurrentHashMap<Long, RunningChat> active =
            new ConcurrentHashMap<>();

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

    public SseEmitter stream(long conversationId, String message) {
        conversationService.requireExist(conversationId);
        RunningChat run = new RunningChat(conversationId, message);

        if (active.putIfAbsent(conversationId, run) != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "该会话已有流式回答正在生成");
        }

        try {
            // 先登记任务，再读取历史、启动线程。
            run.history = messageRepository.findByConversationId(conversationId);
            run.worker.start();
            return run.emitter;
        } catch (RuntimeException ex) {
            active.remove(conversationId, run);
            throw ex;
        }
    }

    public boolean stop(long conversationId) {
        conversationService.requireExist(conversationId);
        RunningChat run = active.get(conversationId);
        return run != null && run.requestEnd("stopped");
    }

    private class RunningChat {

        private final long conversationId;
        private final String message;
        private final SseEmitter emitter = new SseEmitter(120_000L);
        private final StringBuilder answer = new StringBuilder();
        private final Thread worker;
        private List<MessageResponse> history;

        private volatile String requestedStatus;
        private boolean finishing;

        RunningChat(long conversationId, String message) {
            this.conversationId = conversationId;
            this.message = message;
            this.worker = Thread.ofVirtual().unstarted(this::generate);

            emitter.onTimeout(() -> {
                requestEnd("timeout");
                emitter.complete();
            });
            emitter.onError(error -> requestEnd("disconnected"));
            emitter.onCompletion(() -> requestEnd("disconnected"));
        }

        // 一次运行只接受第一个结束请求；落库开始后不再接受停止。
        synchronized boolean requestEnd(String status) {
            if (finishing || requestedStatus != null) {
                return false;
            }
            requestedStatus = status;
            worker.interrupt();
            return true;
        }

        private synchronized String chooseStatus(String generatedStatus) {
            finishing = true;
            return requestedStatus == null ? generatedStatus : requestedStatus;
        }

        private void generate() {
            String generatedStatus = "completed";

            try {
                if (requestedStatus == null) {
                    try (Stream<String> chunks =
                                 chatGateway.stream(message, history).toStream(1)) {
                        Iterator<String> iterator = chunks.iterator();
                        while (iterator.hasNext()) {
                            if (requestedStatus != null) {
                                break;
                            }
                            String chunk = iterator.next();
                            answer.append(chunk);
                            send("delta", Map.of("content", chunk));
                        }
                    }
                }

                if (requestedStatus == null && answer.toString().isBlank()) {
                    throw new IllegalStateException("模型未返回有效回答");
                }
            } catch (IOException ex) {
                generatedStatus = "disconnected";
                log.debug("SSE 连接已断开", ex);
            } catch (RuntimeException ex) {
                generatedStatus = "failed";
                if (requestedStatus == null) {
                    log.warn("模型生成失败，conversationId={}", conversationId, ex);
                }
            } finally {
                String status = chooseStatus(generatedStatus);

                // 停止读取已经完成；清除中断标记后执行最后的 JDBC 保存。
                Thread.interrupted();
                finish(status);
            }
        }

        private void finish(String status) {
            try {
                // 单独处理数据库失败，避免把发送失败误报为保存失败。
                try {
                    transactions.executeWithoutResult(tx -> {
                        messageRepository.save(
                                conversationId, "user", message, "completed");
                        messageRepository.save(
                                conversationId, "assistant", answer.toString(), status);
                    });
                } catch (RuntimeException ex) {
                    log.error("保存聊天结果失败，conversationId={}", conversationId, ex);
                    if (!"timeout".equals(status) && !"disconnected".equals(status)) {
                        try {
                            send("error", Map.of(
                                    "code", "SAVE_FAILED",
                                    "message", "聊天结果保存失败，请查询历史。"
                            ));
                            emitter.complete();
                        } catch (IOException | RuntimeException sendError) {
                            log.debug("无法发送保存失败事件", sendError);
                        }
                    }
                    return;
                }

                // 提交结束后，允许这个会话开始下一轮。
                active.remove(conversationId, this);

                // 超时、断线时，客户端连接可能已无法使用。
                if (!"timeout".equals(status) && !"disconnected".equals(status)) {
                    try {
                        send("done", Map.of("status", status));
                        emitter.complete();
                    } catch (IOException | RuntimeException ex) {
                        log.debug("结束事件发送失败，记录已经保存", ex);
                    }
                }
            } finally {
                // 只移除当前对象，避免误删后续的新任务。
                active.remove(conversationId, this);
            }
        }
        private void send(String event, Map<String, String> data)
                throws IOException {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(data, MediaType.APPLICATION_JSON));
        }
    }
}


