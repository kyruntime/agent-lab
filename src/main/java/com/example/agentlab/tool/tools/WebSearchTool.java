package com.example.agentlab.tool.tools;

import com.example.agentlab.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网页搜索工具，使用百度搜索，提取前几条结果的标题和摘要。
 */
public class WebSearchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final int MAX_RESULTS = 5;

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return "搜索互联网信息，toolInput 传搜索关键词如 \"Java 21 新特性\"";
    }

    @Override
    public String call(String input) {
        try {
            String query = input.trim();
            log.info("搜索关键词: {}", query);
            String url = "https://www.baidu.com/s?wd=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String html = response.body();
            log.debug("百度返回 HTML 长度: {}", html.length());

            List<String> results = extractResults(html);
            log.info("提取到 {} 条结果", results.size());

            if (results.isEmpty()) {
                log.warn("未能从百度 HTML 提取结果，HTML前500字: {}",
                        html.substring(0, Math.min(500, html.length())));
                return "搜索 \"" + query + "\" 未找到结果";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("搜索 \"").append(query).append("\" 的结果：\n");
            for (int i = 0; i < results.size(); i++) {
                sb.append(i + 1).append(". ").append(results.get(i)).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("搜索出错", e);
            return "搜索出错：" + e.getMessage();
        }
    }

    private List<String> extractResults(String html) {
        List<String> results = new ArrayList<>();
        String cleaned = html.replaceAll("<em>", "").replaceAll("</em>", "");

        // 提取百度搜索结果块（class="result" 或 class="c-container"）中的 h3 > a 标题
        Pattern blockPattern = Pattern.compile(
                "<div[^>]*class=\"[^\"]*(?:result|c-container)[^\"]*\"[^>]*>(.*?)</div>\\s*(?=<div[^>]*class=\"[^\"]*(?:result|c-container)|\\.s_tab|$)",
                Pattern.DOTALL);
        Matcher blockMatcher = blockPattern.matcher(cleaned);

        while (blockMatcher.find() && results.size() < MAX_RESULTS) {
            String block = blockMatcher.group(1);
            // 从块里提取标题
            Pattern h3Pattern = Pattern.compile("<h3[^>]*>\\s*<a[^>]*>(.*?)</a>\\s*</h3>", Pattern.DOTALL);
            Matcher h3m = h3Pattern.matcher(block);
            if (h3m.find()) {
                String title = h3m.group(1).replaceAll("<[^>]+>", "").trim().replaceAll("\\s+", " ");
                if (!title.isEmpty() && title.length() > 2) {
                    results.add(title);
                }
            }
        }

        // 兜底：直接找所有 h3 > a
        if (results.isEmpty()) {
            Pattern fallback = Pattern.compile("<h3[^>]*>\\s*<a[^>]*>(.*?)</a>", Pattern.DOTALL);
            Matcher fm = fallback.matcher(cleaned);
            while (fm.find() && results.size() < MAX_RESULTS) {
                String t = fm.group(1).replaceAll("<[^>]+>", "").trim().replaceAll("\\s+", " ");
                if (!t.isEmpty() && t.length() > 4) {
                    results.add(t);
                }
            }
        }

        // 最终兜底：提取页面里任何像搜索结果的文本
        if (results.isEmpty()) {
            Pattern anyTitle = Pattern.compile("<a[^>]*href=\"http[^\"]*\"[^>]*>([^<]{8,80})</a>", Pattern.DOTALL);
            Matcher am = anyTitle.matcher(cleaned);
            while (am.find() && results.size() < MAX_RESULTS) {
                String t = am.group(1).trim();
                if (!t.contains("百度") && !t.contains("登录") && !t.contains("设置")) {
                    results.add(t);
                }
            }
        }

        return results;
    }
}
