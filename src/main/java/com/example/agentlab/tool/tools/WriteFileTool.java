package com.example.agentlab.tool.tools;

import com.example.agentlab.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 写文件工具，类似 Cursor 的 Write 工具。
 * toolInput 格式："文件路径|||文件内容"
 */
public class WriteFileTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WriteFileTool.class);
    private static final String SEPARATOR = "|||";

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "写入文件内容，toolInput 格式为 \"文件路径|||文件内容\"，如 \"/tmp/hello.txt|||Hello World\"";
    }

    @Override
    public String call(String input) {
        int idx = input.indexOf(SEPARATOR);
        if (idx < 0) {
            return "格式错误，请使用 \"文件路径|||文件内容\" 格式";
        }
        String filePath = input.substring(0, idx).trim();
        String content = input.substring(idx + SEPARATOR.length());

        try {
            Path path = Path.of(filePath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, content);
            log.info("写入文件: {} ({}字符)", filePath, content.length());
            return "已写入 " + filePath + "（" + content.length() + " 字符）";
        } catch (Exception e) {
            log.error("写入文件失败: {}", filePath, e);
            return "写入失败：" + e.getMessage();
        }
    }
}
