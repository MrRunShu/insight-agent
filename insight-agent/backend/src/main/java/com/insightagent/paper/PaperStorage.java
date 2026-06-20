package com.insightagent.paper;

import java.io.IOException;

/**
 * Storage abstraction for raw paper files. Decouples "where bytes live" from the rest
 * of the app, so the local-disk implementation can later be swapped for object storage
 * (Tencent COS / S3 / MinIO) without touching callers.
 *
 * <p>Keys are storage-relative paths, e.g. {@code "04_agents_tools/OctoTools.pdf"}.
 */
public interface PaperStorage {

    /** Persist bytes under {@code key}; returns the key. */
    String save(String key, byte[] data) throws IOException;

    /** Read the bytes stored under {@code key}. */
    byte[] load(String key) throws IOException;

    /** Remove the object at {@code key} (no-op if absent). */
    void delete(String key);

    /** Whether an object exists at {@code key}. */
    boolean exists(String key);
}
