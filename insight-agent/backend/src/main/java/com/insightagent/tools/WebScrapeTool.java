package com.insightagent.tools;

import cn.hutool.http.HttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tool: fetch readable text from a URL.
 *
 * <p>Three-tier strategy:
 * <ol>
 *   <li>Direct HTTP GET — fast, works for static pages.</li>
 *   <li>Jina Reader with API key — headless-rendered, clean Markdown; handles JS-heavy sites.</li>
 *   <li>Jina Reader anonymous (free) — automatic fallback if the key quota is exhausted.</li>
 * </ol>
 *
 * <p>Truncates to {@value #MAX_CHARS} chars to stay within model token limits.
 */
@Component
@Slf4j
public class WebScrapeTool {

    private static final int MAX_CHARS = 12_000;
    private static final String JINA_BASE = "https://r.jina.ai/";

    @Value("${app.jina.api-key:}")
    private String jinaApiKey;

    @Tool(description = "Fetch the text content of a web page given its URL. "
            + "Use this to read news articles, blog posts, or any public web page "
            + "before analysing them. Returns plain text / Markdown with HTML stripped.")
    public String fetchWebPage(
            @ToolParam(description = "The full URL of the web page to fetch, including https://")
            String url) {

        log.info("[WebScrapeTool] fetching {}", url);

        // ── Tier 1: direct HTTP GET ───────────────────────────────────────────
        String direct = tryDirectFetch(url);
        if (direct != null) return direct;

        // ── Tier 2: Jina Reader with API key (higher rate limit) ─────────────
        if (jinaApiKey != null && !jinaApiKey.isBlank()) {
            String withKey = tryJinaFetch(url, jinaApiKey);
            if (withKey != null) return withKey;
            log.warn("[WebScrapeTool] Jina (key) failed — retrying in free mode");
        }

        // ── Tier 3: Jina Reader anonymous (free, 20 RPM) ─────────────────────
        String free = tryJinaFetch(url, null);
        if (free != null) return free;

        return "无法获取页面内容：所有抓取方式均失败。URL: " + url;
    }

    private String tryDirectFetch(String url) {
        try {
            String html = HttpRequest.get(url)
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) "
                            + "Chrome/124.0 Safari/537.36")
                    .timeout(10_000)
                    .execute()
                    .body();

            String text = html
                    .replaceAll("<style[^>]*>[\\s\\S]*?</style>", " ")
                    .replaceAll("<script[^>]*>[\\s\\S]*?</script>", " ")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("&[a-z]+;", " ")
                    .replaceAll("\\s{2,}", " ")
                    .trim();

            if (text.length() < 50) {
                log.info("[WebScrapeTool] direct fetch: only {} chars (JS-rendered), falling back to Jina", text.length());
                return null;  // trigger Jina fallback
            }
            log.info("[WebScrapeTool] direct fetch OK: {} chars", text.length());
            return truncate(text);
        } catch (Exception e) {
            log.info("[WebScrapeTool] direct fetch failed ({}), falling back to Jina", e.getMessage());
            return null;
        }
    }

    private String tryJinaFetch(String url, String apiKey) {
        String jinaUrl = JINA_BASE + url;
        String mode = (apiKey != null && !apiKey.isBlank()) ? "key" : "free";
        log.info("[WebScrapeTool] Jina ({}) → {}", mode, jinaUrl);
        try {
            HttpRequest req = HttpRequest.get(jinaUrl)
                    .header("Accept", "text/markdown,text/plain,*/*")
                    .header("X-Return-Format", "markdown")
                    .timeout(30_000);

            if (apiKey != null && !apiKey.isBlank()) {
                req.header("Authorization", "Bearer " + apiKey);
            }

            cn.hutool.http.HttpResponse resp = req.execute();
            int status = resp.getStatus();
            String body = resp.body();

            if (status >= 400) {
                log.warn("[WebScrapeTool] Jina ({}) returned HTTP {}", mode, status);
                return null;
            }
            if (body == null || body.isBlank() || body.length() < 50) {
                log.warn("[WebScrapeTool] Jina ({}) returned too-short body ({} chars)", mode, body == null ? 0 : body.length());
                return null;
            }
            log.info("[WebScrapeTool] Jina ({}) OK: {} chars", mode, body.length());
            return truncate(body);
        } catch (Exception e) {
            log.warn("[WebScrapeTool] Jina ({}) exception: {}", mode, e.getMessage());
            return null;
        }
    }

    private String truncate(String text) {
        if (text.length() > MAX_CHARS) {
            return text.substring(0, MAX_CHARS) + "\n...[truncated]";
        }
        return text;
    }
}
