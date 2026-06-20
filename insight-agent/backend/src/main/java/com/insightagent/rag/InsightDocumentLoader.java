package com.insightagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a PDF (given its bytes) into token-sized chunks for RAG ingestion.
 *
 * <p>Uses Apache PDFBox's standard {@link PDFTextStripper} (robust to symbol fonts with no
 * Unicode mapping, unlike Spring AI's layout stripper). Each chunk is tagged with its source
 * filename and page number; those surface in RAG citations.
 */
@Component
@Slf4j
public class InsightDocumentLoader {

    private final TokenTextSplitter splitter = new TokenTextSplitter();

    /** Result of parsing one PDF: its chunks plus the total page count. */
    public record Parsed(List<Document> chunks, int pageCount) {}

    public Parsed parse(byte[] bytes, String filename) {
        List<Document> pages = new ArrayList<>();
        int total;
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            total = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = 1; p <= total; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                if (text == null || text.isBlank()) {
                    continue;
                }
                Map<String, Object> md = new HashMap<>();
                md.put("source", filename);
                md.put("page_number", p);
                pages.add(new Document(text, md));
            }
        } catch (IOException e) {
            throw new RuntimeException("PDF 解析失败: " + filename + " — " + e.getMessage(), e);
        }
        List<Document> chunks = splitter.apply(pages);
        log.info("Parsed {} → {} page(s) → {} chunk(s)", filename, pages.size(), chunks.size());
        return new Parsed(chunks, total);
    }
}
