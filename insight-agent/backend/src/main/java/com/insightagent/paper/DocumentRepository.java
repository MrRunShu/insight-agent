package com.insightagent.paper;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Persistence for the {@code documents} metadata table (Tier-A "proper" storage layer).
 * Soft delete via {@code deleted_at}; the raw file and vector chunks are handled separately.
 */
@Repository
@Slf4j
public class DocumentRepository {

    private final JdbcTemplate jdbc;

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS documents (
                  id           BIGSERIAL PRIMARY KEY,
                  filename     TEXT UNIQUE NOT NULL,
                  title        TEXT,
                  category     TEXT,
                  content_hash TEXT,
                  page_count   INT,
                  size_bytes   BIGINT,
                  object_key   TEXT,
                  status       TEXT DEFAULT 'ready',
                  created_at   TIMESTAMPTZ DEFAULT now(),
                  updated_at   TIMESTAMPTZ DEFAULT now(),
                  deleted_at   TIMESTAMPTZ
                )
                """);
        log.info("[DocumentRepository] documents table ready");
    }

    public boolean existsActive(String filename) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM documents WHERE filename = ? AND deleted_at IS NULL",
                Integer.class, filename);
        return n != null && n > 0;
    }

    /** Insert, or revive+update an existing row with the same filename (idempotent re-ingest). */
    public void upsert(DocumentMeta d) {
        jdbc.update("""
                INSERT INTO documents(filename, title, category, content_hash, page_count, size_bytes, object_key, status, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ready', NULL)
                ON CONFLICT (filename) DO UPDATE SET
                  title = EXCLUDED.title, category = EXCLUDED.category, content_hash = EXCLUDED.content_hash,
                  page_count = EXCLUDED.page_count, size_bytes = EXCLUDED.size_bytes, object_key = EXCLUDED.object_key,
                  status = 'ready', deleted_at = NULL, updated_at = now()
                """,
                d.filename(), d.title(), d.category(), d.contentHash(),
                d.pageCount(), d.sizeBytes(), d.objectKey());
    }

    public List<DocumentMeta> listActive() {
        return jdbc.query(
                "SELECT * FROM documents WHERE deleted_at IS NULL ORDER BY category NULLS LAST, filename",
                this::map);
    }

    public DocumentMeta findActiveById(long id) {
        return jdbc.query("SELECT * FROM documents WHERE id = ? AND deleted_at IS NULL", this::map, id)
                .stream().findFirst().orElse(null);
    }

    /** Soft delete — mark deleted, keep the row for audit/recovery. */
    public void softDelete(long id) {
        jdbc.update("UPDATE documents SET deleted_at = now(), status = 'deleted', updated_at = now() WHERE id = ?", id);
    }

    public void updateCategory(long id, String category) {
        jdbc.update("UPDATE documents SET category = ?, updated_at = now() WHERE id = ?", category, id);
    }

    private DocumentMeta map(ResultSet rs, int i) throws SQLException {
        return new DocumentMeta(
                rs.getLong("id"),
                rs.getString("filename"),
                rs.getString("title"),
                rs.getString("category"),
                rs.getString("content_hash"),
                (Integer) rs.getObject("page_count"),
                (Long) rs.getObject("size_bytes"),
                rs.getString("object_key"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("deleted_at", OffsetDateTime.class));
    }
}
