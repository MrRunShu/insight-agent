package com.insightagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads academic-paper PDFs from a configurable directory into Spring AI
 * {@link Document} objects for RAG ingestion.
 *
 * <p>Each PDF is read one page per {@link Document} ({@link PagePdfDocumentReader}),
 * then split into ~token-sized chunks ({@link TokenTextSplitter}) so retrieval
 * granularity is appropriate for dense paper text. The source filename is attached
 * as {@code source} metadata and surfaced in RAG citations.
 *
 * <p>Directory is set via {@code app.papers.dir} (defaults to {@code ${user.dir}/papers}).
 */
@Component
@Slf4j
public class InsightDocumentLoader {

    /** Directory holding the academic paper PDFs to ingest. Override via {@code app.papers.dir}. */
    @Value("${app.papers.dir:${user.dir}/papers}")
    private String papersDir;

    private final TokenTextSplitter splitter = new TokenTextSplitter();

    /**
     * Reads all {@code *.pdf} files under {@link #papersDir}, returning them as a
     * flat list of token-sized chunks (one or more per page), each tagged with its
     * source filename.
     */
    public List<Document> loadPapers() {
        List<Document> all = new ArrayList<>();
        File dir = new File(papersDir);
        if (!dir.isDirectory()) {
            log.warn("Papers directory not found: {} — knowledge base will be empty.", dir.getAbsolutePath());
            return all;
        }
        File[] pdfs = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
        if (pdfs == null || pdfs.length == 0) {
            log.warn("No PDF files in {} — knowledge base will be empty.", dir.getAbsolutePath());
            return all;
        }

        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(1)
                .build();

        for (File pdf : pdfs) {
            try {
                PagePdfDocumentReader reader =
                        new PagePdfDocumentReader(new FileSystemResource(pdf), config);
                List<Document> pages = reader.get();
                List<Document> chunks = splitter.apply(pages);
                // Tag every chunk with its source filename for RAG citations.
                chunks.forEach(doc -> doc.getMetadata().put("source", pdf.getName()));
                log.info("Loaded {} page(s) → {} chunk(s) from {}", pages.size(), chunks.size(), pdf.getName());
                all.addAll(chunks);
            } catch (Exception e) {
                log.error("Failed to read PDF {}: {}", pdf.getName(), e.getMessage());
            }
        }
        log.info("Knowledge base total: {} paper chunk(s) loaded from {}", all.size(), papersDir);
        return all;
    }
}
