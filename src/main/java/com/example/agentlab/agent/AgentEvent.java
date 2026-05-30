package com.example.agentlab.agent;

/**
 * Agent 执行过程中的事件，通过 SSE 推送给前端。
 *
 * @param type    事件类型：thinking / tool_call / tool_result / answer / error
 * @param step    当前步骤编号
 * @param content 事件内容
 */
public record AgentEvent(String type, int step, String content) {}
