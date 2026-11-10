package org.example.backend_practice.exception;


//会话不存在报错
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException(long conversationId) {
        super("会话不存在"+conversationId);
    }
}
