package com.insightagent.paper;

import java.time.OffsetDateTime;

/**
 * Metadata row for one paper in the knowledge base. The raw file lives in
 * {@link PaperStorage} under {@link #objectKey}; the DB keeps only this metadata.
 * Chunks in the vector store link back by {@link #filename} (their {@code source}).
 */
public record DocumentMeta(
        Long id,
        String filename,
        String title,
        String category,
        String contentHash,
        Integer pageCount,
        Long sizeBytes,
        String objectKey,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime deletedAt
) {}
