package com.insightagent.controller;

import com.insightagent.rag.InsightDocumentLoader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Manage the personal academic-paper knowledge base: upload new PDFs (parsed,
 * chunked and embedded into the vector store on the fly) and list what is indexed.
 *
 * <p>Lets the user grow the knowledge base from the UI without the old
 * "drop file → TRUNCATE table → restart backend" workflow.
 */
@RestController
@RequestMapping("/papers")
@Tag(name = "Papers", description = "上传与管理个人论文知识库")
@Slf4j
public class PaperController {

    private final InsightDocumentLoader loader;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbc;

    public PaperController(InsightDocumentLoader loader,
                           VectorStore insightVectorStore,
                           JdbcTemplate jdbcTemplate) {
        this.loader = loader;
        this.vectorStore = insightVectorStore;
        this.jdbc = jdbcTemplate;
    }

    @PostMapping("/upload")
    @Operation(summary = "上传一篇论文 PDF，解析并向量化入库（同名论文会先替换旧内容）")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase().endsWith(".pdf")) {
            return Map.<String, Object>of("error", "只支持 PDF 文件");
        }
        // Strip any path components to prevent traversal.
        String name = new File(original).getName();

        File dir = new File(loader.getPapersDir());
        if (!dir.exists() && !dir.mkdirs()) {
            return Map.<String, Object>of("error", "无法创建论文目录: " + dir.getAbsolutePath());
        }
        File dest = new File(dir, name);
        file.transferTo(dest.getAbsoluteFile());

        // Idempotent re-upload: drop any existing chunks with the same source first.
        try {
            vectorStore.delete(new FilterExpressionBuilder().eq("source", name).build());
        } catch (Exception e) {
            log.warn("[PaperController] delete old chunks for {} failed: {}", name, e.getMessage());
        }

        List<Document> chunks;
        try {
            chunks = loader.loadPdf(dest);
        } catch (Exception e) {
            log.warn("[PaperController] parse failed for {}: {}", name, e.getMessage());
            return Map.<String, Object>of("error", "解析失败：" + e.getMessage(), "fileName", name);
        }
        vectorStore.add(chunks);
        log.info("[PaperController] uploaded {} → {} chunk(s) embedded", name, chunks.size());
        return Map.<String, Object>of("fileName", name, "chunks", chunks.size());
    }

    @GetMapping
    @Operation(summary = "列出知识库中已索引的论文及其 chunk 数")
    public List<Map<String, Object>> list() {
        return jdbc.query(
                "SELECT metadata->>'source' AS source, count(*) AS chunks " +
                        "FROM vector_store GROUP BY metadata->>'source' ORDER BY 1",
                (rs, i) -> Map.<String, Object>of(
                        "source", rs.getString("source") == null ? "(unknown)" : rs.getString("source"),
                        "chunks", rs.getInt("chunks")));
    }
}
