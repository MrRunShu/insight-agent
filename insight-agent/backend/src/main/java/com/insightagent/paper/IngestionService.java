package com.insightagent.paper;

import com.insightagent.rag.InsightDocumentLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;

/**
 * Orchestrates paper ingestion: store the file, parse + embed it, and register/refresh
 * its {@code documents} metadata row. Used by both the upload endpoint and startup seeding.
 *
 * <p>Startup seeding walks {@code app.papers.dir}: each immediate sub-folder name becomes a
 * category; PDFs directly in the root fall under "未分类".
 */
@Service
@Slf4j
public class IngestionService {

    private final VectorStore vectorStore;
    private final DocumentRepository repo;
    private final PaperStorage storage;
    private final InsightDocumentLoader parser;

    @Value("${app.papers.dir:${user.dir}/papers}")
    private String papersDir;

    public IngestionService(VectorStore insightVectorStore,
                            DocumentRepository repo,
                            PaperStorage storage,
                            InsightDocumentLoader parser) {
        this.vectorStore = insightVectorStore;
        this.repo = repo;
        this.storage = storage;
        this.parser = parser;
    }

    /**
     * Ingest one paper: persist the file, (re)embed its chunks, upsert its metadata row.
     * Idempotent — re-ingesting the same filename replaces its chunks and revives its row.
     *
     * @return number of chunks embedded
     */
    public int ingest(String filename, String category, byte[] bytes) throws Exception {
        String key = (category != null && !category.isBlank())
                ? category + "/" + filename
                : filename;
        storage.save(key, bytes);

        InsightDocumentLoader.Parsed parsed = parser.parse(bytes, filename);

        // Replace any existing chunks for this filename (idempotent re-ingest).
        try {
            vectorStore.delete(new FilterExpressionBuilder().eq("source", filename).build());
        } catch (Exception e) {
            log.warn("[IngestionService] delete old chunks for {} failed: {}", filename, e.getMessage());
        }
        vectorStore.add(parsed.chunks());

        String title = filename.replaceFirst("(?i)\\.pdf$", "").replace('_', ' ').trim();
        repo.upsert(new DocumentMeta(null, filename, title, category, sha256(bytes),
                parsed.pageCount(), (long) bytes.length, key, "ready", null, null));

        log.info("[IngestionService] ingested {} (category={}, {} chunks)", filename, category, parsed.chunks().size());
        return parsed.chunks().size();
    }

    /** On startup: if the knowledge base has no documents yet, seed from the papers folder. */
    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        if (!repo.listActive().isEmpty()) {
            log.info("[IngestionService] documents present — skipping seed.");
            return;
        }
        File root = new File(papersDir);
        if (!root.isDirectory()) {
            log.warn("[IngestionService] papers dir not found: {} — nothing to seed.", root.getAbsolutePath());
            return;
        }
        log.info("[IngestionService] empty knowledge base — seeding from {}", root.getAbsolutePath());
        int n = 0;
        File[] entries = root.listFiles();
        if (entries != null) {
            for (File e : entries) {
                if (e.isDirectory()) {
                    File[] pdfs = e.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
                    if (pdfs != null) {
                        for (File pdf : pdfs) n += seedOne(pdf, e.getName());
                    }
                } else if (e.getName().toLowerCase().endsWith(".pdf")) {
                    n += seedOne(e, "未分类");
                }
            }
        }
        log.info("[IngestionService] seeded {} paper(s).", n);
    }

    private int seedOne(File pdf, String category) {
        try {
            ingest(pdf.getName(), category, Files.readAllBytes(pdf.toPath()));
            return 1;
        } catch (Exception e) {
            log.error("[IngestionService] seed failed for {}: {}", pdf.getName(), e.getMessage());
            return 0;
        }
    }

    private static String sha256(byte[] b) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(b);
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte x : h) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
