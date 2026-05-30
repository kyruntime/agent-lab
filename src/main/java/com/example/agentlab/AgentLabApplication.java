package com.example.agentlab;

import com.example.agentlab.config.LlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot 应用入口：组装 Web 层、配置绑定与组件扫描骨架。
 *
 * <p>{@code @EnableConfigurationProperties(LlmProperties.class)} 使 {@code agent.llm.*}
 * 成为强类型 Bean，从而让 {@link LlmClient} 实现与控制台配置解耦。</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(LlmProperties.class)
public class AgentLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentLabApplication.class, args);
    }
}
