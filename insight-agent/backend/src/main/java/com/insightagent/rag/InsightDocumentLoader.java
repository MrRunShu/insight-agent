package com.insightagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads academic-paper PDFs into Spring AI {@link Document} objects for RAG ingestion.
 *
 * <p>Text is extracted page-by-page with Apache PDFBox's standard {@link PDFTextStripper},
 * then split into ~token-sized chunks ({@link TokenTextSplitter}). The source filename and
 * page number are attached as metadata and surfaced in RAG citations.
 *
 * <p>We use PDFBox directly rather than Spring AI's {@code PagePdfDocumentReader}: its
 * layout-aware stripper ({@code ForkPDFLayoutTextStripper}) throws
 * {@code StringIndexOutOfBoundsException} on PDFs that embed symbol fonts with no Unicode
 * mapping (e.g. dingbat/math fonts) — common in academic papers. The standard stripper
 * degrades gracefully instead of crashing the whole document.
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

    /** Directory PDFs are read from / uploaded to. */
    public String getPapersDir() {
        return papersDir;
    }

    /**
     * Parse a single PDF into token-sized chunks, each tagged with its source filename
     * and page number. Reused by both startup ingestion and the upload endpoint.
     */
    public List<Document> loadPdf(File pdf) {
        List<Document> pages = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int total = doc.getNumberOfPages();
            for (int p = 1; p <= total; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                if (text == null || text.isBlank()) {
                    continue;
                }
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", pdf.getName());
                metadata.put("page_number", p);
                pages.add(new Document(text, metadata));
            }
        } catch (IOException e) {
            throw new RuntimeException("PDF 解析失败: " + pdf.getName() + " — " + e.getMessage(), e);
        }
        List<Document> chunks = splitter.apply(pages);
        log.info("Parsed {} → {} page(s) → {} chunk(s)", pdf.getName(), pages.size(), chunks.size());
        return chunks;
    }

    /**
     * Reads all {@code *.pdf} files under {@link #papersDir}, returning them as a
     * flat list of token-sized chunks, each tagged with its source filename.
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
        for (File pdf : pdfs) {
            try {
                all.addAll(loadPdf(pdf));
            } catch (Exception e) {
                log.error("Failed to read PDF {}: {}", pdf.getName(), e.getMessage());
            }
        }
        log.info("Knowledge base total: {} paper chunk(s) loaded from {}", all.size(), papersDir);
        return all;
    }
}
