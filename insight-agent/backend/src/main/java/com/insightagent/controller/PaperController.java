package com.insightagent.controller;

import com.insightagent.paper.DocumentMeta;
import com.insightagent.paper.DocumentRepository;
import com.insightagent.paper.IngestionService;
import com.insightagent.paper.PaperStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Manage the personal knowledge base: upload, list (with category), soft-delete, recategorize,
 * and serve the raw PDF (for the in-browser reader).
 */
@RestController
@RequestMapping("/papers")
@Tag(name = "Papers", description = "知识库文档管理（上传/列表/删除/分类/取文件）")
@Slf4j
public class PaperController {

    private final IngestionService ingestion;
    private final DocumentRepository repo;
    private final PaperStorage storage;
    private final VectorStore vectorStore;

    public PaperController(IngestionService ingestion,
                           DocumentRepository repo,
                           PaperStorage storage,
                           VectorStore insightVectorStore) {
        this.ingestion = ingestion;
        this.repo = repo;
        this.storage = storage;
        this.vectorStore = insightVectorStore;
    }

    @GetMapping
    @Operation(summary = "列出知识库中所有论文（含分类、页数）")
    public List<Map<String, Object>> list() {
        return repo.listActive().stream().map(d -> Map.<String, Object>of(
                "id", d.id(),
                "filename", d.filename(),
                "title", d.title() == null ? d.filename() : d.title(),
                "category", d.category() == null ? "未分类" : d.category(),
                "pageCount", d.pageCount() == null ? 0 : d.pageCount()
        )).toList();
    }

    @PostMapping("/upload")
    @Operation(summary = "上传论文 PDF：落盘 + 向量化 + 写元数据（同名替换）")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "category", required = false) String category) throws Exception {
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase().endsWith(".pdf")) {
            return Map.<String, Object>of("error", "只支持 PDF 文件");
        }
        String name = new File(original).getName();
        String cat = (category == null || category.isBlank()) ? "未分类" : category;
        try {
            int chunks = ingestion.ingest(name, cat, file.getBytes());
            return Map.<String, Object>of("filename", name, "category", cat, "chunks", chunks);
        } catch (Exception e) {
            log.warn("[PaperController] upload failed for {}: {}", name, e.getMessage());
            return Map.<String, Object>of("error", "处理失败：" + e.getMessage(), "filename", name);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删除论文（标记删除 + 从检索中移除其向量块；保留原文件）")
    public Map<String, Object> delete(@PathVariable long id) {
        DocumentMeta meta = repo.findActiveById(id);
        if (meta == null) {
            return Map.<String, Object>of("error", "未找到该论文");
        }
        try {
            vectorStore.delete(new FilterExpressionBuilder().eq("source", meta.filename()).build());
        } catch (Exception e) {
            log.warn("[PaperController] delete chunks for {} failed: {}", meta.filename(), e.getMessage());
        }
        repo.softDelete(id);
        log.info("[PaperController] soft-deleted {} (id={})", meta.filename(), id);
        return Map.<String, Object>of("deleted", meta.filename());
    }

    @PatchMapping("/{id}/category")
    @Operation(summary = "修改论文分类（仅改元数据，不动向量）")
    public Map<String, Object> recategorize(@PathVariable long id, @RequestBody Map<String, String> body) {
        if (repo.findActiveById(id) == null) {
            return Map.<String, Object>of("error", "未找到该论文");
        }
        String category = body.getOrDefault("category", "未分类");
        repo.updateCategory(id, category);
        return Map.<String, Object>of("id", id, "category", category);
    }

    @GetMapping("/{id}/file")
    @Operation(summary = "取论文原始 PDF（供前端阅读器渲染）")
    public ResponseEntity<byte[]> file(@PathVariable long id) throws Exception {
        DocumentMeta meta = repo.findActiveById(id);
        if (meta == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = storage.load(meta.objectKey());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + meta.filename() + "\"")
                .body(bytes);
    }
}
