package com.example.agentlab.tool.tools;

import com.example.agentlab.tool.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 读取文件内容，类似 Cursor 的 Read 工具。
 * 限制：最多读取前 3000 字符。
 */
public class ReadFileTool implements Tool {

    private static final int MAX_CONTENT_LENGTH = 3000;

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "读取文件内容，toolInput 传文件路径如 \"/tmp/test.txt\" 或 \"pom.xml\"";
    }

    @Override
    public String call(String input) {
        try {
            Path path = Path.of(input.trim());
            if (!Files.exists(path)) {
                return "文件不存在：" + input;
            }
            if (Files.isDirectory(path)) {
                StringBuilder sb = new StringBuilder("目录内容：\n");
                Files.list(path).limit(50).forEach(p ->
                        sb.append(Files.isDirectory(p) ? "[目录] " : "       ")
                                .append(p.getFileName())
                                .append("\n"));
                return sb.toString().trim();
            }
            String content = Files.readString(path);
            if (content.length() > MAX_CONTENT_LENGTH) {
                return content.substring(0, MAX_CONTENT_LENGTH) + "\n...(文件内容已截断，共" + content.length() + "字符)";
            }
            return content;
        } catch (IOException e) {
            return "读取失败：" + e.getMessage();
        }
    }
}
