package com.insightagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RAG vector-store configuration — PGVector (PostgreSQL + pgvector extension).
 *
 * <p>Builds the store and creates the {@code vector_store} table (HNSW, COSINE, 1024-dim
 * BGE-M3). Document seeding is handled separately by
 * {@code IngestionService} after the app is ready (so it can also populate the
 * {@code documents} metadata table).
 */
@Configuration
@Slf4j
public class InsightVectorStoreConfig {

    @Bean
    VectorStore insightVectorStore(JdbcTemplate jdbcTemplate,
                                   EmbeddingModel ollamaEmbeddingModel) {
        PgVectorStore store = PgVectorStore.builder(jdbcTemplate, ollamaEmbeddingModel)
                .dimensions(1024)                                          // BGE-M3 vector size
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)                                    // create table if absent
                .build();
        store.afterPropertiesSet();                                       // create vector_store table now
        log.info("PGVector store ready (seeding deferred to IngestionService).");
        return store;
    }
}
