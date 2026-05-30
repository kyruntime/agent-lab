package com.example.agentlab.web;

import com.example.agentlab.agent.AgentService;
import com.example.agentlab.agent.ChatSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PostMapping("/agent/run")
    public Map<String, String> run(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        if (question == null || question.isBlank()) {
            return Map.of("error", "question 不能为空");
        }
        try {
            ChatSession session = resolveSession(request.get("sessionId"));
            log.info("[{}] 收到请求：{}", session.getId(), question);
            String answer = agentService.run(session, question);
            return Map.of("answer", answer, "sessionId", session.getId());
        } catch (Exception e) {
            log.error("Agent 执行异常", e);
            return Map.of("error", e.getMessage());
        }
    }

    @GetMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String question,
                             @RequestParam(required = false) String sessionId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        ChatSession session = resolveSession(sessionId);
        log.info("[{}] SSE 流式请求：{}", session.getId(), question);

        final var completed = new java.util.concurrent.atomic.AtomicBoolean(false);
        emitter.onCompletion(() -> completed.set(true));
        emitter.onTimeout(() -> completed.set(true));
        emitter.onError(e -> completed.set(true));

        executor.execute(() -> {
            agentService.runStream(session, question, event -> {
                if (completed.get()) return;
                try {
                    emitter.send(SseEmitter.event()
                            .name(event.type())
                            .data(objectMapper.writeValueAsString(event)));
                } catch (Exception e) {
                    log.error("SSE 发送失败", e);
                    completed.set(true);
                }
            });
            if (!completed.get()) {
                try {
                    emitter.send(SseEmitter.event().name("done").data("{}"));
                    emitter.complete();
                } catch (Exception e) {
                    log.warn("SSE 完成时异常: {}", e.getMessage());
                }
            }
        });

        return emitter;
    }

    private ChatSession resolveSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            ChatSession session = agentService.getSession(sessionId);
            if (session != null) return session;
            log.warn("会话不存在: {}，创建新会话", sessionId);
        }
        return agentService.createSession();
    }
}
