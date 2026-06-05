package com.insightagent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool: fetch and extract readable text content from a URL.
 *
 * <p>Strips HTML tags so the model receives plain text, not markup.
 * Truncates to {@value #MAX_CHARS} chars to stay within token limits.
 */
@Slf4j
public class WebScrapeTool {

    /** Maximum characters returned to the model (≈ 3 000 tokens for DeepSeek). */
    private static final int MAX_CHARS = 12_000;

    @Tool(description = "Fetch the text content of a web page given its URL. "
            + "Use this to read news articles, blog posts, or any public web page "
            + "before analysing them. Returns plain text with HTML stripped.")
    public String fetchWebPage(
            @ToolParam(description = "The full URL of the web page to fetch, including https://")
            String url) {

        log.info("[WebScrapeTool] fetching {}", url);
        try {
            String html = HttpRequest.get(url)
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) "
                            + "Chrome/124.0 Safari/537.36")
                    .timeout(10_000)
                    .execute()
                    .body();

            // Strip HTML tags and collapse whitespace
            String text = html
                    .replaceAll("<style[^>]*>[\\s\\S]*?</style>", " ")
                    .replaceAll("<script[^>]*>[\\s\\S]*?</script>", " ")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("&[a-z]+;", " ")
                    .replaceAll("\\s{2,}", " ")
                    .trim();

            if (text.length() > MAX_CHARS) {
                text = text.substring(0, MAX_CHARS) + "\n...[truncated]";
            }
            log.info("[WebScrapeTool] fetched {} chars from {}", text.length(), url);
            return text;

        } catch (Exception e) {
            log.warn("[WebScrapeTool] failed to fetch {}: {}", url, e.getMessage());
            return "Error fetching page: " + e.getMessage();
        }
    }
}
