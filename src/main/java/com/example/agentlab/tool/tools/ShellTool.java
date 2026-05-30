package com.example.agentlab.tool.tools;

import com.example.agentlab.tool.Tool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * 执行系统命令，类似 Cursor 的 Shell 工具。
 * 限制：最多 30 秒超时，输出截取前 2000 字符。
 */
public class ShellTool implements Tool {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LENGTH = 2000;

    @Override
    public String name() {
        return "shell";
    }

    @Override
    public String description() {
        return "执行终端命令，toolInput 传命令如 \"ls -la\" 或 \"date\"。注意：只能执行安全的只读命令";
    }

    @Override
    public String call(String input) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", input.trim());
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                    if (sb.length() > MAX_OUTPUT_LENGTH) {
                        sb.append("...(输出已截断)");
                        break;
                    }
                }
            }

            boolean finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return "命令超时（超过" + TIMEOUT_SECONDS + "秒）";
            }

            String output = sb.toString().trim();
            int exitCode = proc.exitValue();
            if (exitCode != 0) {
                return "退出码=" + exitCode + "\n" + output;
            }
            return output.isEmpty() ? "(无输出)" : output;
        } catch (Exception e) {
            return "执行出错：" + e.getMessage();
        }
    }
}
