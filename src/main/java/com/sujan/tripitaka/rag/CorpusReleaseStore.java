package com.sujan.tripitaka.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * SQL pointer to the published release of each collection (same idea as RunbookPublicationStore).
 * Both indexes are written first; only then does this pointer move, so readers never see half a release.
 */
@Repository
public class CorpusReleaseStore {

    private final JdbcTemplate jdbc;

    public CorpusReleaseStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> current(String collection) {
        return jdbc.queryForList("SELECT release_id FROM corpus_releases WHERE collection = ?",
                String.class, collection).stream().findFirst();
    }

    @Transactional
    public void publish(String collection, String release, int chunkCount, Optional<String> expectedPrevious) {
        var now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        if (expectedPrevious.isEmpty()) {
            // The primary key rejects a concurrent first publication.
            jdbc.update("""
                    INSERT INTO corpus_releases (collection, release_id, chunk_count, published_at)
                    VALUES (?, ?, ?, ?)
                    """, collection, release, chunkCount, now);
            return;
        }
        int changed = jdbc.update("""
                UPDATE corpus_releases SET release_id = ?, chunk_count = ?, published_at = ?
                WHERE collection = ? AND release_id = ?
                """, release, chunkCount, now, collection, expectedPrevious.get());
        if (changed != 1) {
            throw new IllegalStateException("Release changed concurrently; rerun ingestion");
        }
    }
}
