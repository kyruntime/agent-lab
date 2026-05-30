package com.example.agentlab.mcp;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * MCP Server 暴露的一个工具的描述信息，对应 MCP 协议 tools/list 返回的每个 tool。
 *
 * @param name        工具名称
 * @param description 工具描述
 * @param inputSchema 工具参数的 JSON Schema（可选）
 */
public record McpTool(String name, String description, JsonNode inputSchema) {}
