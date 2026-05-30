package com.example.agentlab.agent;

import com.example.agentlab.tool.ToolRegistry;

/**
 * Prompt 构造器：负责生成 system prompt 和 user prompt。
 *
 * system prompt 包含工具描述（从 ToolRegistry 自动生成）和返回格式要求。
 * user prompt 包含用户问题和历史 observation。
 */
public class PromptBuilder {

    private final ToolRegistry toolRegistry;

    public PromptBuilder(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * Function calling 模式下，system prompt 只需要描述 Agent 的角色和行为准则。
     * 工具描述由 API 的 tools 参数传递，不需要在 prompt 里重复。
     */
    public String buildSystemPrompt() {
        return """
            你是一个智能助手，能够通过调用工具来回答用户问题。

            行为准则：
            1. 尽量用最少的步骤完成任务（通常 1-3 步足够）
            2. 如果一次工具调用的结果已经足够回答问题，直接给出回答
            3. 对于不需要工具的简单问题，直接回答即可
            4. 回答要准确、简洁、有帮助
        """;
    }
}
