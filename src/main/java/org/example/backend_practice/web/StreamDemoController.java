package org.example.backend_practice.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class StreamDemoController {

    private static final Logger log =
            LoggerFactory.getLogger(StreamDemoController.class);

    @GetMapping(
            value = "/api/stream-demo",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter stream() {
        // 本次异步请求的超时时间：30 秒。
        SseEmitter emitter = new SseEmitter(30_000L);

        // 创建工作线程，稍后启动。
        Thread worker = Thread.ofVirtual().unstarted(() -> {
            try {
                List<String> chunks = List.of(
                        "你好，",
                        "我是你的编程助教。",
                        "这段内容正在分段发送。",
                        "你已经开始学习 SSE 了。"
                );

                for (String chunk : chunks) {
                    // 用延迟模拟文本逐段产生。
                    Thread.sleep(500);

                    emitter.send(
                            SseEmitter.event()
                                    .name("delta")
                                    .data(
                                            Map.of("content", chunk),
                                            MediaType.APPLICATION_JSON
                                    )
                    );
                }

                // 全部文本发送完，通知客户端正常结束。
                emitter.send(
                        SseEmitter.event()
                                .name("done")
                                .data(
                                        Map.of("status", "completed"),
                                        MediaType.APPLICATION_JSON
                                )
                );

                // 完成本次 HTTP 响应。
                emitter.complete();

            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();

            } catch (IOException ex) {
                // 发送失败时，由 Servlet 容器处理连接清理。
                log.debug("SSE 发送失败，连接可能已断开", ex);

            } catch (RuntimeException ex) {
                emitter.completeWithError(ex);
            }
        });

        emitter.onTimeout(() -> {
            worker.interrupt();
            emitter.complete();
        });

        emitter.onError(error -> worker.interrupt());
        emitter.onCompletion(worker::interrupt);

        worker.start();
        return emitter;
    }
}