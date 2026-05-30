package com.example.agentlab.tool.tools;

import com.example.agentlab.tool.Tool;

public class ClockTool implements Tool {

    @Override
    public String name() {
        return "clock";
    }

    @Override
    public String description() {
        return "获取今天日期，toolInput 传空字符串 \"\"";
    }

    @Override
    public String call(String input) {
        return java.time.LocalDate.now().toString();
    }
}
