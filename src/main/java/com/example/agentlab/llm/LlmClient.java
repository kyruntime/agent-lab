package com.example.agentlab.llm;

import com.example.agentlab.agent.ChatMessage;
import com.example.agentlab.config.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * 调用阿里云 DashScope 大模型的客户端。
 */
@Slf4j
@Component
public class LlmClient {

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public LlmClient(LlmProperties properties) {
        this.apiUrl = properties.getBaseUrl() + "/chat/completions";
        this.apiKey = properties.getApiKey();
        this.model = properties.getModel();
        this.mapper = new ObjectMapper();
        this.httpClient = createHttpClient();
        log.info("LLM 客户端初始化：model={}, url={}", model, apiUrl);
    }

    /** 简单两条消息的聊天（兼容旧调用方式） */
    public String chat(String systemPrompt, String userMessage) throws Exception {
        return chat(List.of(ChatMessage.system(systemPrompt), ChatMessage.user(userMessage)));
    }

    /** 支持完整消息列表的多轮对话 */
    public String chat(List<ChatMessage> messageList) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        ArrayNode messages = body.putArray("messages");
        for (ChatMessage msg : messageList) {
            messages.addObject().put("role", msg.role()).put("content", msg.content());
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        log.debug("请求 LLM：model={}", model);
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            log.error("LLM 调用失败 HTTP {}：{}", resp.statusCode(), resp.body());
            throw new RuntimeException("LLM 调用失败 HTTP " + resp.statusCode() + "：" + resp.body());
        }

        JsonNode root = mapper.readTree(resp.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode()) {
            throw new RuntimeException("响应中找不到 content：" + resp.body());
        }
        return content.asText();
    }

    /**
     * 带 function calling 的 LLM 调用。
     * 传入 tools 定义，LLM 可能返回 tool_calls 或直接回答。
     */
    public LlmResponse chatWithTools(List<ChatMessage> messageList, List<Map<String, Object>> tools) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);

        ArrayNode messages = body.putArray("messages");
        for (ChatMessage msg : messageList) {
            ObjectNode msgNode = messages.addObject();
            msgNode.put("role", msg.role());
            if (msg.content() != null) {
                msgNode.put("content", msg.content());
            }
            if ("tool".equals(msg.role()) && msg.toolCallId() != null) {
                msgNode.put("tool_call_id", msg.toolCallId());
            }
            if ("assistant".equals(msg.role()) && msg.toolCalls() != null) {
                msgNode.set("tool_calls", mapper.valueToTree(msg.toolCalls()));
            }
        }

        if (tools != null && !tools.isEmpty()) {
            body.set("tools", mapper.valueToTree(tools));
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        String requestBody = mapper.writeValueAsString(body);
        log.info("========== LLM 请求 ==========");
        log.info("请求 LLM：model={}, 消息数={}, tools={}", model, messageList.size(), tools != null ? tools.size() : 0);
        log.info("请求体：\n{}", prettyJson(requestBody));

        long start = System.currentTimeMillis();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;

        log.info("========== LLM 响应 ({}ms) ==========", elapsed);

        if (resp.statusCode() != 200) {
            log.error("LLM 调用失败 HTTP {}：\n{}", resp.statusCode(), prettyJson(resp.body()));
            throw new RuntimeException("LLM 调用失败 HTTP " + resp.statusCode() + "：" + resp.body());
        }

        log.info("响应体：\n{}", prettyJson(resp.body()));

        JsonNode root = mapper.readTree(resp.body());
        JsonNode message = root.path("choices").path(0).path("message");

        // 提取 token 用量
        JsonNode usage = root.path("usage");
        if (!usage.isMissingNode()) {
            log.info("LLM 响应 ({}ms)：prompt_tokens={}, completion_tokens={}, total_tokens={}",
                    elapsed, usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(), usage.path("total_tokens").asInt());
        } else {
            log.info("LLM 响应 ({}ms)", elapsed);
        }

        String content = message.has("content") && !message.get("content").isNull()
                ? message.get("content").asText() : null;

        String reasoning = message.has("reasoning_content") && !message.get("reasoning_content").isNull()
                ? message.get("reasoning_content").asText() : null;
        if (reasoning != null) {
            log.info("LLM 思考过程：{}", reasoning.length() > 200 ? reasoning.substring(0, 200) + "..." : reasoning);
        }

        List<LlmResponse.ToolCall> toolCalls = new ArrayList<>();
        if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
            for (JsonNode tc : message.get("tool_calls")) {
                String id = tc.path("id").asText();
                String funcName = tc.path("function").path("name").asText();
                String arguments = tc.path("function").path("arguments").asText();
                toolCalls.add(new LlmResponse.ToolCall(id, funcName, arguments));
            }
        }

        if (!toolCalls.isEmpty()) {
            for (var tc : toolCalls) {
                String prettyArgs = prettyJson(tc.arguments());
                log.info("LLM 请求调用工具：{}({})", tc.functionName(), prettyArgs);
            }
        } else {
            log.info("LLM 直接回答：{}", content != null && content.length() > 100 ? content.substring(0, 100) + "..." : content);
        }

        return new LlmResponse(content, reasoning, toolCalls);
    }

    private String prettyJson(String json) {
        try {
            Object obj = mapper.readValue(json, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return json;
        }
    }

    private static HttpClient createHttpClient() {
        try {
            TrustManager[] trustAll = { new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String t) {}
                public void checkServerTrusted(X509Certificate[] c, String t) {}
            }};
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new SecureRandom());
            return HttpClient.newBuilder().sslContext(sc).build();
        } catch (Exception e) {
            return HttpClient.newHttpClient();
        }
    }
}
