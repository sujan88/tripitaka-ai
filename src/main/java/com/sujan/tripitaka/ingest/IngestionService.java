package com.sujan.tripitaka.ingest;

import com.sujan.tripitaka.model.SourceText;
import com.sujan.tripitaka.rag.Bm25Store;
import com.sujan.tripitaka.rag.CorpusReleaseStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import com.sujan.tripitaka.rag.VectorStores;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Ingests a whole collection as one immutable release:
 * hash -> skip if already published -> stage in Lucene + Qdrant -> move the SQL pointer.
 * A failure anywhere before publish leaves the previous release serving.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final int BATCH_SIZE = 100;

    private final VectorStores vectorStores;
    private final Bm25Store bm25Store;
    private final CorpusReleaseStore releases;
    private final PassageChunker chunker = new PassageChunker(2400);

    public IngestionService(VectorStores vectorStores, Bm25Store bm25Store, CorpusReleaseStore releases) {
        this.vectorStores = vectorStores;
        this.bm25Store = bm25Store;
        this.releases = releases;
    }

    public Result ingest(String collection, List<SourceText> texts) throws IOException {
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("No texts found to ingest");
        }
        // Chunk ids derive from textId, so duplicates would silently overwrite each other in Qdrant.
        Set<String> textIds = new HashSet<>();
        for (SourceText text : texts) {
            if (!textIds.add(text.textId())) {
                throw new IllegalArgumentException("Duplicate text id: " + text.textId());
            }
        }
        String release = releaseOf(collection, texts);
        Optional<String> previous = releases.current(collection);
        if (previous.isPresent() && previous.get().equals(release)) {
            log.info("Collection {} already at release {}; nothing to do", collection, release);
            return new Result(collection, release, 0, false);
        }

        List<Document> chunks = new ArrayList<>();
        for (SourceText text : texts) {
            chunks.addAll(chunker.chunk(collection, release, text));
        }
        if (chunks.isEmpty()) {
            // Publishing this would replace a working release with an empty one (e.g. scanned PDFs).
            throw new IllegalArgumentException("No extractable text found to ingest");
        }
        log.info("Staging collection={} release={} texts={} chunks={}", collection, release, texts.size(), chunks.size());

        bm25Store.clearRelease(collection, release);
        for (int start = 0; start < chunks.size(); start += BATCH_SIZE) {
            List<Document> batch = chunks.subList(start, Math.min(start + BATCH_SIZE, chunks.size()));
            bm25Store.add(batch);
            vectorStores.forCollection(collection).add(batch); // embedding calls happen here; same ids upsert on rerun
            log.info("Staged {}/{} chunks", Math.min(start + BATCH_SIZE, chunks.size()), chunks.size());
        }
        bm25Store.commit();
        releases.publish(collection, release, chunks.size(), previous);
        log.info("Published collection={} release={} chunks={}", collection, release, chunks.size());

        // Only now is the old release unused, so it can go: no duplicate chunks are kept.
        pruneRetired(collection);
        return new Result(collection, release, chunks.size(), true);
    }

    /** Deletes every release of this collection except the published one, from Qdrant and from BM25. */
    public void pruneRetired(String collection) throws IOException {
        Optional<String> current = releases.current(collection);
        if (current.isEmpty()) {
            log.warn("Nothing published for collection={}; not pruning", collection);
            return;
        }
        var filters = new FilterExpressionBuilder();
        vectorStores.forCollection(collection).delete(filters.and(filters.eq("collection", collection), filters.ne("release", current.get())).build());
        bm25Store.deleteOtherReleases(collection, current.get());
        log.info("Pruned retired releases collection={} kept={}", collection, current.get());
    }

    static String releaseOf(String collection, List<SourceText> texts) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, collection);
            update(digest, PassageChunker.VERSION);
            for (SourceText text : texts) {
                update(digest, text.textId());
                update(digest, text.title());
                update(digest, text.nikaya());
                if (text.searchHeader() != null) {
                    update(digest, text.searchHeader()); // only when present, so existing releases keep their hash
                }
                for (SourceText.Passage passage : text.passages()) {
                    update(digest, passage.reference());
                    update(digest, passage.text());
                }
            }
            return HexFormat.of().formatHex(digest.digest()).substring(0, 32);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0x1f);
    }

    public record Result(String collection, String release, int chunks, boolean published) {
    }
}
