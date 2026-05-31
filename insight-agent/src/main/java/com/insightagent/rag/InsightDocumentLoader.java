package com.insightagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads Markdown knowledge-base documents from {@code classpath:document/*.md}
 * into Spring AI {@link Document} objects for RAG ingestion.
 *
 * <p>Each top-level section (separated by horizontal rules {@code ---}) becomes
 * its own Document so retrieval granularity matches the knowledge structure.
 */
@Component
@Slf4j
public class InsightDocumentLoader implements ResourceLoaderAware {

    private ResourcePatternResolver resolver;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
    }

    /**
     * Reads all {@code *.md} files under {@code classpath:document/} and returns
     * them as a flat list of Documents (one per section / horizontal-rule block).
     */
    public List<Document> loadMarkdowns() {
        List<Document> all = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)   // each --- block = 1 Document
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("source", fileName)
                        .build();
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                List<Document> docs = reader.get();
                log.info("Loaded {} document(s) from {}", docs.size(), fileName);
                all.addAll(docs);
            }
        } catch (IOException e) {
            log.error("Failed to load knowledge-base documents", e);
        }
        log.info("Knowledge base total: {} document chunks loaded", all.size());
        return all;
    }
}
