package com.insightagent.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Persistence for paper annotations (highlights / notes). Hybrid anchoring:
 * <ul>
 *   <li>geometry: {@code page} + {@code rects} (jsonb, normalized 0–1) — for drawing & hit-test</li>
 *   <li>text anchor: {@code quote}/{@code prefix}/{@code suffix} — for search, robustness, LLM context</li>
 * </ul>
 * Linked to a paper by {@code filename} (same key the vector chunks use).
 */
@Repository
@Slf4j
public class AnnotationRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;

    public AnnotationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS annotations (
                  id         BIGSERIAL PRIMARY KEY,
                  filename   TEXT NOT NULL,
                  page       INT,
                  rects      JSONB,
                  quote      TEXT,
                  prefix     TEXT,
                  suffix     TEXT,
                  color      TEXT DEFAULT 'yellow',
                  note       TEXT,
                  source     TEXT DEFAULT 'manual',
                  created_at TIMESTAMPTZ DEFAULT now()
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_annotations_filename ON annotations(filename)");
        log.info("[AnnotationRepository] annotations table ready");
    }

    public long create(String filename, Integer page, String rectsJson, String quote,
                       String prefix, String suffix, String color, String note, String source) {
        Long id = jdbc.queryForObject("""
                INSERT INTO annotations(filename, page, rects, quote, prefix, suffix, color, note, source)
                VALUES (?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                filename, page, rectsJson, quote, prefix, suffix,
                color == null ? "yellow" : color, note, source == null ? "manual" : source);
        return id == null ? -1 : id;
    }

    public List<Map<String, Object>> listByFilename(String filename) {
        return jdbc.query(
                "SELECT * FROM annotations WHERE filename = ? ORDER BY page, id",
                (rs, i) -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("page", rs.getObject("page"));
                    m.put("rects", parse(rs.getString("rects")));
                    m.put("quote", rs.getString("quote"));
                    m.put("color", rs.getString("color"));
                    m.put("note", rs.getString("note"));
                    m.put("source", rs.getString("source"));
                    return m;
                }, filename);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM annotations WHERE id = ?", id);
    }

    public void updateNote(long id, String note) {
        jdbc.update("UPDATE annotations SET note = ? WHERE id = ?", note, id);
    }

    private static Object parse(String json) {
        if (json == null) return null;
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}
