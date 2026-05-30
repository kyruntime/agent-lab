# agent-lab

`agent-lab` 是一个 Spring Boot 学习型项目，用来从零理解 LLM、Agent、Tool、MCP 的基本原理。

不依赖 LangChain4j、Spring AI、MCP SDK 等框架，而是用原生 Java HttpClient + OpenAI Function Calling 协议，手写完整的 Agent Loop。

## 架构

```
┌─────────────┐     SSE/REST      ┌──────────────────┐
│  index.html │ ◄──────────────► │ AgentController   │
└─────────────┘                   └────────┬─────────┘
                                           │
                                  ┌────────▼─────────┐
                                  │   AgentService    │
                                  │  (ReAct Loop)     │
                                  └───┬──────────┬────┘
                                      │          │
                           ┌──────────▼──┐   ┌───▼──────────┐
                           │  LlmClient   │   │ ToolRegistry  │
                           │ (DashScope)  │   └───┬──────────┘
                           └─────────────┘       │
                                    ┌────────────┼────────────┐
                                    │            │            │
                              本地 6 工具    McpToolAdapter   ...
                                                 │
                                          ┌──────▼──────┐
                                          │ StdioMcpClient│
                                          └──────┬──────┘
                                                 │ stdio
                                          ┌──────▼──────┐
                                          │ MCP Server  │
                                          └─────────────┘
```

## 模块说明

- **`llm`** — 封装 DashScope OpenAI 兼容接口调用，支持普通 chat 和 Function Calling
- **`agent`** — ReAct 主循环，同步/流式两种执行模式，会话管理与持久化
- **`tool`** — 本地工具系统，`ToolRegistry` 自动注册并生成 OpenAI tools 格式
- **`mcp`** — MCP stdio 客户端，启动时自动发现并注册远程工具
- **`web`** — REST + SSE API，内置 ChatGPT 风格聊天 UI
- **`config`** — `agent.llm.*` 配置属性绑定

## 内置工具

| 工具 | 功能 |
|------|------|
| `clock` | 当前日期 |
| `calculator` | 数学表达式计算 |
| `shell` | 执行 shell 命令 |
| `read_file` | 读文件 / 列目录 |
| `write_file` | 写文件 |
| `web_search` | 百度搜索 |

## 快速开始

### 环境要求

- Java 17+
- Maven 3.6+

### 配置 API Key

```bash
export DASHSCOPE_API_KEY=你的API_KEY
```

### 启动

```bash
mvn spring-boot:run
```

打开 http://localhost:8080 即可使用内置聊天 UI。

## API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/agent/run` | POST | 同步执行，body: `{ "question": "..." }` |
| `/api/agent/stream` | GET (SSE) | 流式执行，query: `question` |

支持通过 `sessionId` 参数实现多轮会话。

## 配置说明

`src/main/resources/application.yml`：

```yaml
agent:
  max-steps: 5          # Agent 最大推理步数
  llm:
    provider: aliyun-dashscope
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    api-key: ${DASHSCOPE_API_KEY}
    model: qwen3.6-max-preview
    temperature: 0.2
    max-tokens: 1024
```

如果 `qwen3.6-max-preview` 在你的控制台不可用，可以改成其他可用的模型 ID。

## MCP 配置

支持通过 stdio 连接外部 MCP Server，在 `application.yml` 中配置：

```yaml
mcp:
  servers:
    - bash,tools/test-mcp-server.sh
```

启动时会自动连接、发现工具并注册到 `ToolRegistry`，Agent 主循环无需区分本地/远程工具。

## 技术栈

- Java 17 + Spring Boot 3.3
- Java 原生 HttpClient（无第三方 HTTP 库）
- Lombok
- 前端：原生 HTML/CSS/JS + SSE

## 演进路线

- [x] JSON 决策 Agent（已被替代）
- [x] 接入 OpenAI Function Calling
- [x] 接入 MCP Client（stdio）
- [x] 会话持久化（文件）
- [ ] Skill 标准化
- [ ] Memory 增强
- [ ] 更多工具（企业微信/数据库/浏览器等）
