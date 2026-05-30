package com.example.agentlab.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * MCP (Model Context Protocol) 客户端接口。
 *
 * MCP 协议的核心就三步：
 * 1. initialize —— 握手，协商协议版本
 * 2. tools/list —— 获取 Server 提供的工具清单
 * 3. tools/call —— 调用某个工具并拿到结果
 */
public interface McpClient extends AutoCloseable {

    /** 与 MCP Server 握手，协商协议版本和能力 */
    void initialize() throws Exception;

    /** 获取 Server 提供的所有工具 */
    List<McpTool> listTools() throws Exception;

    /** 调用指定工具，返回结果 */
    JsonNode callTool(String toolName, JsonNode arguments) throws Exception;
}
