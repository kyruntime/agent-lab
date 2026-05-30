package com.example.agentlab.agent;

import com.example.agentlab.llm.LlmClient;
import com.example.agentlab.llm.LlmResponse;
import com.example.agentlab.tool.Tool;
import com.example.agentlab.tool.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ReAct Agent 核心服务，支持多轮对话记忆。
 */
@Slf4j
@Service
public class AgentService {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int maxSteps;

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private final Path sessionsDir;

    public AgentService(
            LlmClient llmClient,
            ToolRegistry toolRegistry,
            @Value("${agent.max-steps:5}") int maxSteps
    ) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.promptBuilder = new PromptBuilder(toolRegistry);
        this.maxSteps = maxSteps;
        this.sessionsDir = Path.of(System.getProperty("user.home"), ".agent-lab", "sessions");
        try {
            Files.createDirectories(sessionsDir);
        } catch (IOException e) {
            log.warn("创建 session 目录失败：{}", e.getMessage());
        }
        loadSessions();
        log.info("Agent 初始化完成，最大步数={}，已加载{}个历史会话", maxSteps, sessions.size());
    }

    /** 创建新会话 */
    public ChatSession createSession() {
        ChatSession session = new ChatSession();
        sessions.put(session.getId(), session);
        log.info("创建会话：{}", session.getId());
        return session;
    }

    /** 获取已有会话 */
    public ChatSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /** 持久化单个会话到文件 */
    private void saveSession(ChatSession session) {
        try {
            Path file = sessionsDir.resolve(session.getId() + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), Map.of(
                    "id", session.getId(),
                    "messages", session.getHistory()
            ));
        } catch (Exception e) {
            log.warn("保存会话失败 {}: {}", session.getId(), e.getMessage());
        }
    }

    /** 启动时加载所有历史会话 */
    private void loadSessions() {
        try {
            if (!Files.exists(sessionsDir)) return;
            Files.list(sessionsDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            JsonNode root = mapper.readTree(p.toFile());
                            String id = root.path("id").asText();
                            ChatSession session = new ChatSession(id);
                            for (JsonNode msg : root.path("messages")) {
                                session.addMessage(new ChatMessage(
                                        msg.path("role").asText(),
                                        msg.has("content") && !msg.get("content").isNull() ? msg.get("content").asText() : null
                                ));
                            }
                            sessions.put(id, session);
                        } catch (Exception e) {
                            log.warn("加载会话失败 {}: {}", p.getFileName(), e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("遍历会话目录失败：{}", e.getMessage());
        }
    }

    /** 无状态调用（兼容旧接口） */
    public String run(String question) throws Exception {
        ChatSession session = createSession();
        return run(session, question);
    }

    /**
     * 同步版本的 Agent 调用（使用 function calling）。
     */
    public String run(ChatSession session, String question) throws Exception {
        log.info("[{}] 收到问题：{}", session.getId(), question);
        String systemPrompt = promptBuilder.buildSystemPrompt();
        var tools = toolRegistry.toOpenAITools();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.addAll(session.getHistory());
        messages.add(ChatMessage.user(question));

        for (int i = 1; i <= maxSteps; i++) {
            LlmResponse llmResp = llmClient.chatWithTools(messages, tools);

            if (!llmResp.hasToolCalls()) {
                String answer = llmResp.content() != null ? llmResp.content() : "";
                session.addMessage(ChatMessage.user(question));
                session.addMessage(ChatMessage.assistant(answer));
                saveSession(session);
                return answer;
            }

            List<Object> rawToolCalls = new ArrayList<>();
            for (var tc : llmResp.toolCalls()) {
                rawToolCalls.add(Map.of(
                        "id", tc.id(),
                        "type", "function",
                        "function", Map.of("name", tc.functionName(), "arguments", tc.arguments())
                ));
            }
            messages.add(ChatMessage.assistantWithToolCalls(rawToolCalls));

            for (var tc : llmResp.toolCalls()) {
                String toolInput = extractInput(tc.arguments());
                var toolOpt = toolRegistry.find(tc.functionName());
                String result = toolOpt.isPresent()
                        ? toolOpt.get().call(toolInput)
                        : "工具 " + tc.functionName() + " 不存在";
                log.info("[{}] 第{}步 {}({}) → {}", session.getId(), i, tc.functionName(), toolInput,
                        result.length() > 100 ? result.substring(0, 100) + "..." : result);
                messages.add(ChatMessage.toolResult(tc.id(), result));
            }
        }
        return "达到最大步数 " + maxSteps + "，未得出答案。";
    }

    /**
     * 流式执行（使用原生 Function Calling）：
     * LLM 通过 API 的 tools 参数获知可用工具，响应中直接返回 tool_calls 结构，
     * 不再需要手工解析 JSON。
     */
    public void runStream(ChatSession session, String question, java.util.function.Consumer<AgentEvent> emit) {
        try {
            emit.accept(new AgentEvent("session", 0, session.getId()));
            String systemPrompt = promptBuilder.buildSystemPrompt();
            var tools = toolRegistry.toOpenAITools();

            // 构建消息列表：system + 历史 + 当前问题
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(systemPrompt));
            messages.addAll(session.getHistory());
            messages.add(ChatMessage.user(question));

            for (int i = 1; i <= maxSteps; i++) {
                log.info("[{}] 第{}步 开始推理, 消息数={}", session.getId(), i, messages.size());
                emit.accept(new AgentEvent("thinking", i, "LLM 推理中..."));

                long start = System.currentTimeMillis();
                LlmResponse llmResp = llmClient.chatWithTools(messages, tools);
                long elapsed = System.currentTimeMillis() - start;

                // 推送 LLM 的思考过程（reasoning_content，qwen 模型特有）
                if (llmResp.reasoning() != null && !llmResp.reasoning().isEmpty()) {
                    emit.accept(new AgentEvent("thinking", i, llmResp.reasoning()));
                }

                if (!llmResp.hasToolCalls()) {
                    // LLM 直接回答，没有调用工具
                    String answer = llmResp.content() != null ? llmResp.content() : "（空回答）";
                    log.info("[{}] 第{}步 LLM 直接回答 ({}ms): {}", session.getId(), i, elapsed,
                            answer.length() > 100 ? answer.substring(0, 100) + "..." : answer);
                    session.addMessage(ChatMessage.user(question));
                    session.addMessage(ChatMessage.assistant(answer));
                    saveSession(session);
                    emit.accept(new AgentEvent("answer", i, answer));
                    return;
                }

                // LLM 请求调用工具
                log.info("[{}] 第{}步 LLM 请求调用 {} 个工具 ({}ms)", session.getId(), i, llmResp.toolCalls().size(), elapsed);

                // 先把 assistant 的 tool_calls 消息加入上下文
                List<Object> rawToolCalls = new ArrayList<>();
                for (var tc : llmResp.toolCalls()) {
                    rawToolCalls.add(Map.of(
                            "id", tc.id(),
                            "type", "function",
                            "function", Map.of("name", tc.functionName(), "arguments", tc.arguments())
                    ));
                }
                messages.add(ChatMessage.assistantWithToolCalls(rawToolCalls));

                // 执行每个工具调用
                for (var tc : llmResp.toolCalls()) {
                    String funcName = tc.functionName();
                    String argsJson = tc.arguments();

                    // 从 arguments JSON 中提取 input 参数
                    String toolInput = extractInput(argsJson);

                    log.info("[{}] 第{}步 调用工具 {}({})", session.getId(), i, funcName, toolInput);
                    emit.accept(new AgentEvent("thinking", i, "需要调用 " + funcName));
                    emit.accept(new AgentEvent("tool_call", i, "调用 " + funcName + "(" + toolInput + ")"));

                    var toolOpt = toolRegistry.find(funcName);
                    String result;
                    if (toolOpt.isEmpty()) {
                        result = "工具 " + funcName + " 不存在，可用工具：" + toolRegistry.toolNames();
                        log.warn("[{}] 第{}步 {}", session.getId(), i, result);
                    } else {
                        result = toolOpt.get().call(toolInput);
                        log.info("[{}] 第{}步 工具返回: {}", session.getId(), i,
                                result.length() > 200 ? result.substring(0, 200) + "..." : result);
                    }

                    emit.accept(new AgentEvent("tool_result", i, funcName + " 返回：" + result));

                    // 把工具结果作为 tool 消息加入上下文
                    messages.add(ChatMessage.toolResult(tc.id(), result));
                }
            }
            emit.accept(new AgentEvent("error", maxSteps, "达到最大步数"));
        } catch (Exception e) {
            log.error("[{}] Agent 执行异常", session.getId(), e);
            emit.accept(new AgentEvent("error", 0, e.getMessage()));
        }
    }

    private String extractInput(String argsJson) {
        try {
            JsonNode args = mapper.readTree(argsJson);
            if (args.has("input")) {
                return args.get("input").asText();
            }
            return argsJson;
        } catch (Exception e) {
            return argsJson;
        }
    }
}
