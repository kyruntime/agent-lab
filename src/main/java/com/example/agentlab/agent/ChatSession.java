package com.example.agentlab.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 会话：保存一次多轮对话的所有消息历史。
 *
 * 每个 session 有唯一 id，Controller 通过 id 关联同一会话的多次请求。
 */
public class ChatSession {

    private final String id;
    private final List<ChatMessage> history = new ArrayList<>();

    public ChatSession() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
    }

    public ChatSession(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public List<ChatMessage> getHistory() {
        return history;
    }

    public void addMessage(ChatMessage message) {
        history.add(message);
    }
}
