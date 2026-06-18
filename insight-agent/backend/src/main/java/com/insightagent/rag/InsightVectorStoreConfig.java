package com.insightagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RAG vector-store configuration — PGVector (PostgreSQL + pgvector extension).
 *
 * <p>Replaces the Phase 4 {@code SimpleVectorStore} (in-memory, lost on restart).
 * Schema is auto-created on first startup via {@code initialize-schema: true}.
 * Documents are embedded only when the table is empty — subsequent restarts skip
 * the expensive embedding step and reuse what is already in the database.
 */
@Configuration
@Slf4j
public class InsightVectorStoreConfig {

    @Autowired
    private InsightDocumentLoader documentLoader;

    /**
     * Parameter name {@code ollamaEmbeddingModel} must match the Spring AI
     * auto-configured bean name for the Ollama embedding model.
     */
    @Bean
    VectorStore insightVectorStore(JdbcTemplate jdbcTemplate,
                                   EmbeddingModel ollamaEmbeddingModel) {

        PgVectorStore store = PgVectorStore.builder(jdbcTemplate, ollamaEmbeddingModel)
                .dimensions(1024)                                          // BGE-M3 vector size
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)                                    // create table if absent
                .build();

        // Trigger schema initialisation now (creates vector_store table + HNSW index).
        // Normally Spring calls afterPropertiesSet() after the @Bean method returns,
        // but we need the table to exist before the COUNT query below.
        store.afterPropertiesSet();

        // Only embed on first run — skip if docs are already persisted
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store", Integer.class);
        if (count == null || count == 0) {
            log.info("Vector store is empty — embedding academic paper documents…");
            store.add(documentLoader.loadPapers());
            log.info("Knowledge base embedded and persisted to PGVector.");
        } else {
            log.info("Vector store has {} documents — reusing existing embeddings.", count);
        }
        return store;
    }
}
