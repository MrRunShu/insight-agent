package com.insightagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG vector-store configuration.
 *
 * <p>Uses {@link SimpleVectorStore} (in-memory) backed by BGE-M3 via Ollama.
 * All knowledge-base Markdown documents are loaded and embedded at startup.
 *
 * <p>Phase 5 will replace this with PGVector for persistence; see
 * the commented-out vectorstore config in application.yml.
 */
@Configuration
@Slf4j
public class InsightVectorStoreConfig {

    @Autowired
    private InsightDocumentLoader documentLoader;

    /**
     * The parameter name {@code ollamaEmbeddingModel} must match the Spring AI
     * auto-configured bean name for the Ollama embedding model.
     */
    @Bean
    VectorStore insightVectorStore(EmbeddingModel ollamaEmbeddingModel) {
        log.info("Initialising in-memory vector store with BGE-M3 embeddings…");
        SimpleVectorStore store = SimpleVectorStore.builder(ollamaEmbeddingModel).build();
        store.add(documentLoader.loadMarkdowns());
        log.info("Vector store ready.");
        return store;
    }
}
