package com.example.agentlab.tool;

import java.util.Map;

/**
 * 工具接口：每个工具实现这些方法即可被 Agent 自动发现和调用。
 */
public interface Tool {

    /** 工具的唯一名称，LLM 通过这个名称来调用 */
    String name();

    /** 工具的描述，告诉 LLM 这个工具能干什么 */
    String description();

    /**
     * 工具参数的 JSON Schema 描述，用于 OpenAI function calling 格式。
     * 默认返回单个 input 字符串参数。
     */
    default Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "input", Map.of("type", "string", "description", "工具输入参数")
                ),
                "required", new String[]{"input"}
        );
    }

    /** 执行工具，接收 LLM 传来的 input 字符串，返回结果字符串 */
    String call(String input);
}
