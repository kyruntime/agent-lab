#!/bin/bash
# 最小化 MCP Server 测试脚本
# 支持 initialize、tools/list、tools/call 三个方法

while IFS= read -r line; do
    id=$(echo "$line" | python3 -c "import json,sys; print(json.loads(sys.stdin.read()).get('id',''))" 2>/dev/null)
    method=$(echo "$line" | python3 -c "import json,sys; print(json.loads(sys.stdin.read()).get('method',''))" 2>/dev/null)

    case "$method" in
        "initialize")
            echo "{\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":{\"protocolVersion\":\"2024-11-05\",\"serverInfo\":{\"name\":\"test-server\",\"version\":\"1.0.0\"},\"capabilities\":{\"tools\":{}}}}"
            ;;
        "notifications/initialized")
            # 通知不需要响应
            ;;
        "tools/list")
            echo "{\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":{\"tools\":[{\"name\":\"get_time\",\"description\":\"获取当前时间\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"timezone\":{\"type\":\"string\",\"description\":\"时区，如 Asia/Shanghai\"}},\"required\":[]}},{\"name\":\"echo\",\"description\":\"回显输入内容\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"message\":{\"type\":\"string\",\"description\":\"要回显的消息\"}},\"required\":[\"message\"]}}]}}"
            ;;
        "tools/call")
            tool_name=$(echo "$line" | python3 -c "import json,sys; print(json.loads(sys.stdin.read()).get('params',{}).get('name',''))" 2>/dev/null)
            case "$tool_name" in
                "get_time")
                    now=$(date '+%Y-%m-%d %H:%M:%S')
                    echo "{\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"当前时间: $now\"}]}}"
                    ;;
                "echo")
                    msg=$(echo "$line" | python3 -c "import json,sys; print(json.loads(sys.stdin.read()).get('params',{}).get('arguments',{}).get('message','hello'))" 2>/dev/null)
                    echo "{\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"Echo: $msg\"}]}}"
                    ;;
                *)
                    echo "{\"jsonrpc\":\"2.0\",\"id\":$id,\"error\":{\"code\":-32601,\"message\":\"Unknown tool: $tool_name\"}}"
                    ;;
            esac
            ;;
    esac
done
