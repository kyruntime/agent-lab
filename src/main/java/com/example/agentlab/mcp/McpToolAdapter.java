package com.example.agentlab.mcp;

import com.example.agentlab.tool.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 把 MCP 远程工具适配成本地 Tool 接口。
 *
 * 这样 ToolRegistry 不用区分"本地工具"还是"MCP 远程工具"，
 * Agent 主循环也完全不用改——统一通过 Tool 接口调用。
 */
public class McpToolAdapter implements Tool {

    private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);

    private final McpClient client;
    private final McpTool mcpTool;
    private final ObjectMapper mapper = new ObjectMapper();

    public McpToolAdapter(McpClient client, McpTool mcpTool) {
        this.client = client;
        this.mcpTool = mcpTool;
    }

    @Override
    public String name() {
        return mcpTool.name();
    }

    @Override
    public String description() {
        return mcpTool.description();
    }

    @Override
    public Map<String, Object> parameters() {
        if (mcpTool.inputSchema() != null) {
            try {
                return mapper.treeToValue(mcpTool.inputSchema(), Map.class);
            } catch (Exception e) {
                // fallback
            }
        }
        return Tool.super.parameters();
    }

    @Override
    public String call(String input) {
        log.info("调用 MCP 工具 [{}]，参数: {}", mcpTool.name(),
                input != null && input.length() > 200 ? input.substring(0, 200) + "..." : input);
        long start = System.currentTimeMillis();
        try {
            JsonNode arguments;
            if (input == null || input.isBlank()) {
                arguments = mapper.createObjectNode();
            } else if (input.trim().startsWith("{")) {
                arguments = mapper.readTree(input);
            } else {
                var node = mapper.createObjectNode();
                node.put("input", input);
                arguments = node;
            }

            JsonNode result = client.callTool(mcpTool.name(), arguments);

            String output;
            JsonNode content = result.path("content");
            if (content.isArray() && !content.isEmpty()) {
                output = content.get(0).path("text").asText(result.toString());
            } else {
                output = result.toString();
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("MCP 工具 [{}] 完成 ({}ms)，结果: {}", mcpTool.name(), elapsed,
                    output.length() > 300 ? output.substring(0, 300) + "..." : output);
            return output;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("MCP 工具 [{}] 调用失败 ({}ms): {}", mcpTool.name(), elapsed, e.getMessage());
            return "MCP 工具调用失败: " + e.getMessage();
        }
    }
}
