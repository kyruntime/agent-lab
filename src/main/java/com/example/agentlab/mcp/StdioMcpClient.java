package com.example.agentlab.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通过 stdin/stdout 与 MCP Server 进程通信的客户端实现。
 *
 * MCP 协议底层用 JSON-RPC 2.0，传输层用 stdio：
 * - 每条消息是一行 JSON（以换行符分隔）
 * - Client 写 stdin → Server 读取并处理 → Server 写 stdout → Client 读取结果
 */
public class StdioMcpClient implements McpClient {

    private static final Logger log = LoggerFactory.getLogger(StdioMcpClient.class);
    private static final int READ_TIMEOUT_SECONDS = 30;

    private final String[] command;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger requestId = new AtomicInteger(1);

    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;

    /**
     * @param command 启动 MCP Server 的命令，例如 "npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp"
     */
    public StdioMcpClient(String... command) {
        this.command = command;
    }

    @Override
    public void initialize() throws Exception {
        String cmdStr = String.join(" ", command);
        log.info("启动 MCP Server 进程: {}", cmdStr);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        process = pb.start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        // 异步读 stderr 防止进程阻塞
        new Thread(() -> {
            try (var errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errReader.readLine()) != null) {
                    log.debug("[MCP stderr] {}", line);
                }
            } catch (IOException ignored) {}
        }, "mcp-stderr-" + cmdStr).start();

        ObjectNode params = mapper.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        ObjectNode clientInfo = params.putObject("clientInfo");
        clientInfo.put("name", "agent-lab");
        clientInfo.put("version", "1.0.0");
        params.putObject("capabilities");

        JsonNode initResult = sendRequest("initialize", params);
        log.info("MCP 握手完成: {}", initResult);

        sendNotification("notifications/initialized", null);
    }

    @Override
    public List<McpTool> listTools() throws Exception {
        JsonNode result = sendRequest("tools/list", mapper.createObjectNode());
        List<McpTool> tools = new ArrayList<>();
        JsonNode toolsNode = result.path("tools");
        if (toolsNode.isArray()) {
            for (JsonNode t : toolsNode) {
                tools.add(new McpTool(
                        t.path("name").asText(),
                        t.path("description").asText(""),
                        t.get("inputSchema")
                ));
            }
        }
        return tools;
    }

    @Override
    public JsonNode callTool(String toolName, JsonNode arguments) throws Exception {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", arguments != null ? arguments : mapper.createObjectNode());
        JsonNode result = sendRequest("tools/call", params);
        return result;
    }

    @Override
    public void close() throws Exception {
        if (process != null && process.isAlive()) {
            writer.close();
            process.destroyForcibly();
        }
    }

    // ========== JSON-RPC 通信 ==========

    private JsonNode sendRequest(String method, JsonNode params) throws Exception {
        int id = requestId.getAndIncrement();

        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null) {
            request.set("params", params);
        }

        String line = mapper.writeValueAsString(request);
        log.info("MCP 请求 [id={}] {}: {}", id, method,
                line.length() > 500 ? line.substring(0, 500) + "..." : line);

        writer.write(line);
        writer.newLine();
        writer.flush();

        long start = System.currentTimeMillis();

        // 带超时的读取，避免永久阻塞
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            while (true) {
                Future<String> future = executor.submit(() -> reader.readLine());
                String responseLine;
                try {
                    responseLine = future.get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    throw new IOException("MCP Server 响应超时（" + READ_TIMEOUT_SECONDS + "秒）");
                }

                if (responseLine == null) {
                    throw new IOException("MCP Server 进程意外关闭");
                }

                JsonNode resp = mapper.readTree(responseLine);
                if (resp.has("id") && resp.get("id").asInt() == id) {
                    long elapsed = System.currentTimeMillis() - start;
                    if (resp.has("error")) {
                        log.error("MCP 响应 [id={}] 错误 ({}ms): {}", id, elapsed, resp.get("error"));
                        throw new RuntimeException("MCP 错误: " + resp.get("error"));
                    }
                    JsonNode result = resp.get("result");
                    String resultStr = result != null ? result.toString() : "null";
                    log.info("MCP 响应 [id={}] ({}ms): {}", id, elapsed,
                            resultStr.length() > 500 ? resultStr.substring(0, 500) + "..." : resultStr);
                    return result;
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void sendNotification(String method, JsonNode params) throws Exception {
        ObjectNode notification = mapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        if (params != null) {
            notification.set("params", params);
        }

        writer.write(mapper.writeValueAsString(notification));
        writer.newLine();
        writer.flush();
    }
}
