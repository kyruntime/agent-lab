package com.example.agentlab.tool;

import com.example.agentlab.tool.tools.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    public ToolRegistry() {
        register(new ClockTool());
        register(new CalculatorTool());
        register(new ShellTool());
        register(new ReadFileTool());
        register(new WriteFileTool());
        register(new WebSearchTool());
        log.info("工具注册表初始化完成，共{}个工具：{}", tools.size(),
                tools.keySet().stream().sorted().collect(Collectors.joining(", ")));
    }

    public void register(Tool tool) {
        tools.put(tool.name(), tool);
        log.debug("注册工具：{}", tool.name());
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public String toolNames() {
        return tools.keySet().stream().sorted().collect(Collectors.joining(", "));
    }

    public String toPromptText() {
        return tools.values().stream()
                .map(t -> "- " + t.name() + "：" + t.description())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 生成 OpenAI function calling 格式的 tools 数组。
     * 格式: [{"type":"function","function":{"name":"xxx","description":"xxx","parameters":{...}}}]
     */
    public List<Map<String, Object>> toOpenAITools() {
        return tools.values().stream()
                .map(t -> Map.<String, Object>of(
                        "type", "function",
                        "function", Map.of(
                                "name", t.name(),
                                "description", t.description(),
                                "parameters", t.parameters()
                        )
                ))
                .collect(Collectors.toList());
    }
}
