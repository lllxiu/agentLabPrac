package org.example.backend_practice.service;

import jakarta.annotation.PreDestroy;
import org.example.backend_practice.ai.ChatGateway;
import org.example.backend_practice.dto.MessageResponse;
import org.example.backend_practice.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
@DependsOn("chatDraftRecovery")
public class ChatStreamService {

    private static final Logger log =
            LoggerFactory.getLogger(ChatStreamService.class);

    private final ConversationService conversationService;
    private final MessageRepository messageRepository;
    private final ChatGateway chatGateway;
    private final TransactionTemplate transactions;
    private final ScheduledExecutorService draftScheduler;

    private final ConcurrentHashMap<Long, RunningChat> active =
            new ConcurrentHashMap<>();

    public ChatStreamService(
            ConversationService conversationService,
            MessageRepository messageRepository,
            ChatGateway chatGateway,
            PlatformTransactionManager transactionManager,
            @Qualifier("chatDraftScheduler") ScheduledExecutorService draftScheduler) {
        this.conversationService = conversationService;
        this.messageRepository = messageRepository;
        this.chatGateway = chatGateway;
        this.transactions = new TransactionTemplate(transactionManager);
        this.draftScheduler = draftScheduler;
    }

    public SseEmitter stream(long conversationId, String message) {
        conversationService.requireExist(conversationId);
        RunningChat run = new RunningChat(conversationId, message);

        if (active.putIfAbsent(conversationId, run) != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "该会话已有流式回答正在生成");
        }

        try {
            // 历史必须在插入本轮问题之前读取，避免重复发送当前问题。
            run.history = messageRepository.findByConversationId(conversationId);

            run.ids = transactions.execute(tx -> new TurnIds(
                    messageRepository.insert(
                            conversationId, "user", message, "completed"),
                    messageRepository.insert(
                            conversationId, "assistant", "", "generating")
            ));

            run.worker.start();
            return run.emitter;
        } catch (RuntimeException ex) {
            // 两次插入失败会一起回滚；已提交但未能启动的草稿标记为失败。
            if (run.ids != null) {
                try {
                    messageRepository.finishDraft(
                            run.ids.assistantMessageId(), "", "failed");
                } catch (RuntimeException saveError) {
                    ex.addSuppressed(saveError);
                }
            }
            active.remove(conversationId, run);
            throw ex;
        }
    }

    public boolean stop(long conversationId) {
        conversationService.requireExist(conversationId);
        RunningChat run = active.get(conversationId);
        return run != null && run.requestEnd("stopped");
    }

    @PreDestroy
    public void shutdown() {
        active.values().forEach(run -> run.requestEnd("interrupted"));
    }

    private record TurnIds(long userMessageId, long assistantMessageId) {
    }

    private class RunningChat {

        private final long conversationId;
        private final String message;
        private final SseEmitter emitter = new SseEmitter(120_000L);
        private final StringBuilder answer = new StringBuilder();
        private final Thread worker;

        // 串行执行草稿 UPDATE 和最终 UPDATE，防止旧草稿覆盖最终结果。
        private final Object persistenceLock = new Object();
        private List<MessageResponse> history;
        private TurnIds ids;
        private ScheduledFuture<?> draftTask;

        private volatile String requestedStatus;
        private boolean finishing;
        private long revision;
        private long persistedRevision;

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

        synchronized boolean requestEnd(String status) {
            if (finishing || requestedStatus != null) {
                return false;
            }
            requestedStatus = status;
            worker.interrupt();
            return true;
        }

        private synchronized boolean append(String chunk) {
            if (requestedStatus != null) {
                return false;
            }
            answer.append(chunk);
            if (!chunk.isEmpty()) {
                revision++;
            }
            return true;
        }

        private synchronized String chooseStatus(String generatedStatus) {
            finishing = true;
            return requestedStatus == null ? generatedStatus : requestedStatus;
        }

        private void flushDraft() {
            try {
                synchronized (persistenceLock) {
                    String snapshot;
                    long snapshotRevision;

                    synchronized (this) {
                        if (finishing || requestedStatus != null
                                || revision == persistedRevision) {
                            return;
                        }
                        snapshot = answer.toString();
                        snapshotRevision = revision;
                    }

                    messageRepository.updateDraft(
                            ids.assistantMessageId(), snapshot);

                    synchronized (this) {
                        persistedRevision = snapshotRevision;
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("草稿保存失败，conversationId={}", conversationId, ex);
                requestEnd("failed");
            }
        }

        private void generate() {
            String generatedStatus = "completed";

            try {
                send("meta", Map.of(
                        "conversationId", conversationId,
                        "userMessageId", ids.userMessageId(),
                        "assistantMessageId", ids.assistantMessageId()
                ));

                if (requestedStatus == null) {
                    // 与文本到达无关：即使模型暂时停顿，也会保存已有的新内容。
                    draftTask = draftScheduler.scheduleWithFixedDelay(
                            this::flushDraft, 1, 1, TimeUnit.SECONDS);

                    try (Stream<String> chunks =
                                 chatGateway.stream(message, history).toStream(1)) {
                        Iterator<String> iterator = chunks.iterator();
                        while (iterator.hasNext()) {
                            String chunk = iterator.next();
                            if (!append(chunk)) {
                                break;
                            }
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
                if (draftTask != null) {
                    draftTask.cancel(false);
                }
                Thread.interrupted();
                finish(status);
            }
        }

        private void finish(String status) {
            String content;
            synchronized (this) {
                content = answer.toString();
            }

            try {
                try {
                    synchronized (persistenceLock) {
                        // 一条 UPDATE 原子地保存最终正文和状态。
                        messageRepository.finishDraft(
                                ids.assistantMessageId(), content, status);
                    }
                } catch (RuntimeException ex) {
                    log.error("保存最终回答失败，conversationId={}", conversationId, ex);
                    if (canSendResult(status)) {
                        try {
                            send("error", Map.of(
                                    "code", "SAVE_FAILED",
                                    "message", "最终回答保存失败，请查询已有草稿。"
                            ));
                            emitter.complete();
                        } catch (IOException | RuntimeException sendError) {
                            log.debug("无法发送保存失败事件", sendError);
                        }
                    }
                    return;
                }

                active.remove(conversationId, this);
                if (canSendResult(status)) {
                    try {
                        send("done", Map.of(
                                "status", status,
                                "assistantMessageId", ids.assistantMessageId()
                        ));
                        emitter.complete();
                    } catch (IOException | RuntimeException ex) {
                        log.debug("结束事件发送失败，记录已经保存", ex);
                    }
                }
            } finally {
                active.remove(conversationId, this);
            }
        }

        private boolean canSendResult(String status) {
            return !"timeout".equals(status) && !"disconnected".equals(status);
        }

        private void send(String event, Map<String, ?> data) throws IOException {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(data, MediaType.APPLICATION_JSON));
        }
    }
}

