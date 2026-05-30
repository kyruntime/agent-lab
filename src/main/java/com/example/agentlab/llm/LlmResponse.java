package com.example.agentlab.llm;

import java.util.List;

/**
 * LLM 响应的结构化表示。
 *
 * 两种情况：
 * 1. LLM 直接回答：content 有值，toolCalls 为空
 * 2. LLM 请求调用工具：content 可能为空，toolCalls 有值
 */
public record LlmResponse(
        String content,
        String reasoning,
        List<ToolCall> toolCalls
) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public record ToolCall(String id, String functionName, String arguments) {}
}
