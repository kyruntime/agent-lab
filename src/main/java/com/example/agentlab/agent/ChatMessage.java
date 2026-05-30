package com.example.agentlab.agent;

import java.util.List;

/**
 * 对话消息，对应 LLM API 的 messages 数组中的一条。
 * 扩展支持 tool 角色（function calling 的工具结果返回）。
 */
public record ChatMessage(String role, String content, String toolCallId, List<Object> toolCalls) {

    public ChatMessage(String role, String content) {
        this(role, content, null, null);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    public static ChatMessage assistantWithToolCalls(List<Object> toolCalls) {
        return new ChatMessage("assistant", null, null, toolCalls);
    }

    public static ChatMessage toolResult(String toolCallId, String content) {
        return new ChatMessage("tool", content, toolCallId, null);
    }
}
