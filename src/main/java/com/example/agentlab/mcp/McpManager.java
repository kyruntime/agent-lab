package com.example.agentlab.mcp;

import com.example.agentlab.tool.ToolRegistry;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP 客户端管理器：启动时根据配置连接 MCP Server，发现并注册远程工具。
 *
 * 配置示例（application.yml）：
 *   mcp:
 *     servers:
 *       - npx,-y,@anthropic-ai/mcp-server-time
 *       - bash,tools/test-mcp-server.sh
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "mcp")
@Getter
@Setter
public class McpManager implements CommandLineRunner {

    private final ToolRegistry toolRegistry;
    private List<String> servers = new ArrayList<>();
    private final List<McpClient> clients = new ArrayList<>();

    public McpManager(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void run(String... args) {
        if (servers.isEmpty()) {
            log.info("未配置 MCP Server，跳过");
            return;
        }

        for (String cmdLine : servers) {
            if (cmdLine == null || cmdLine.isBlank()) continue;
            connectServer(cmdLine.split(","));
        }
    }

    /**
     * 连接单个 MCP Server，发现工具并注册到 ToolRegistry。
     */
    public void connectServer(String... command) {
        String cmdStr = String.join(" ", command);
        log.info("连接 MCP Server: {}", cmdStr);
        try {
            StdioMcpClient client = new StdioMcpClient(command);
            client.initialize();
            clients.add(client);

            List<McpTool> tools = client.listTools();
            log.info("MCP Server [{}] 提供 {} 个工具", cmdStr, tools.size());

            for (McpTool tool : tools) {
                McpToolAdapter adapter = new McpToolAdapter(client, tool);
                toolRegistry.register(adapter);
                log.info("  注册 MCP 工具：{} - {}", tool.name(), tool.description());
            }
        } catch (Exception e) {
            log.error("连接 MCP Server 失败 [{}]: {}", cmdStr, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        for (McpClient client : clients) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("关闭 MCP 客户端失败: {}", e.getMessage());
            }
        }
        log.info("所有 MCP 客户端已关闭");
    }
}
