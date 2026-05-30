package com.example.agentlab.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 将 {@code application.yml} 中 {@code agent.llm.*} 绑定为类型安全的配置对象。
 *
 * <p>{@link com.example.agentlab.llm.LlmClient} 从这里读取 Endpoint、密钥与解码参数，
 * 避免在代码中散落魔法字符串；API Key 通过环境变量或外部配置注入，禁止硬编码入库。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "agent.llm")
public class LlmProperties {

    /**
     * 提供商标签（如 {@code aliyun-dashscope}），便于未来按该字段切换多种 LLM 实现。
     */
    private String provider;

    /**
     * OpenAI 兼容 Chat Completions 的基础 URL（不含 {@code /chat/completions} 路径段），例如 DashScope compatible-mode。
     */
    private String baseUrl;

    /**
     * 远端鉴权 Bearer Token，通常映射自 {@code DASHSCOPE_API_KEY} 等环境变量。
     */
    private String apiKey;

    /**
     * 具体模型 ID，决定能力、价格与上下文长度上限。
     */
    private String model;

    /**
     * 采样温度：越高输出越发散，Agent 决策 JSON 往往需要偏低以保证可解析。
     */
    private double temperature = 0.2;

    /**
     * 单次补全生成的 token 上限，防止工具尚未返回超长 JSON 时已截断模型输出。
     */
    private int maxTokens = 1024;
}
