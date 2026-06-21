package com.insightagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insightagent.paper.AnnotationRepository;
import com.insightagent.paper.DocumentMeta;
import com.insightagent.paper.DocumentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Paper annotations (highlights / notes). Stored per paper with hybrid anchoring
 * (geometry + text quote). See {@link AnnotationRepository}.
 */
@RestController
@Tag(name = "Annotations", description = "论文标注：高亮 / 笔记")
@Slf4j
public class AnnotationController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DocumentRepository docs;
    private final AnnotationRepository repo;

    public AnnotationController(DocumentRepository docs, AnnotationRepository repo) {
        this.docs = docs;
        this.repo = repo;
    }

    @GetMapping("/papers/{id}/annotations")
    @Operation(summary = "列出某论文的全部标注")
    public List<Map<String, Object>> list(@PathVariable long id) {
        DocumentMeta meta = docs.findActiveById(id);
        if (meta == null) return List.of();
        return repo.listByFilename(meta.filename());
    }

    @PostMapping("/papers/{id}/annotations")
    @Operation(summary = "新建标注（高亮/笔记）")
    public Map<String, Object> create(@PathVariable long id, @RequestBody Map<String, Object> body) throws Exception {
        DocumentMeta meta = docs.findActiveById(id);
        if (meta == null) return Map.of("error", "未找到该论文");
        String rectsJson = MAPPER.writeValueAsString(body.getOrDefault("rects", List.of()));
        Integer page = body.get("page") instanceof Number n ? n.intValue() : null;
        long annId = repo.create(
                meta.filename(), page, rectsJson,
                str(body, "quote"), str(body, "prefix"), str(body, "suffix"),
                str(body, "color"), str(body, "note"), str(body, "source"));
        return Map.of("id", annId);
    }

    @DeleteMapping("/annotations/{annId}")
    @Operation(summary = "删除标注")
    public Map<String, Object> delete(@PathVariable long annId) {
        repo.delete(annId);
        return Map.of("deleted", annId);
    }

    @PatchMapping("/annotations/{annId}")
    @Operation(summary = "修改标注笔记")
    public Map<String, Object> patch(@PathVariable long annId, @RequestBody Map<String, String> body) {
        repo.updateNote(annId, body.get("note"));
        return Map.of("id", annId);
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }
}
