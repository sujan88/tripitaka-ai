package com.sujan.tripitaka.rag;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Hybrid retrieval: Qdrant similarity + Lucene BM25 -> RRF -> optional rerank.
 * Decoupled from any conversation/ownership logic, so it can be reused and evaluated on its own.
 */
@Service
public class TripitakaRetriever {

    private static final Logger log = LoggerFactory.getLogger(TripitakaRetriever.class);
    private static final int MAX_CHUNK_CHARS = 6000;
    /** With several layers: candidates per pool, and at most this many non-sutta passages per answer. */
    private static final int PRIMARY_POOL = 50; // same candidates as a suttas-only search (top-k x 2)
    private static final int OTHER_POOL = 20;
    private static final int MAX_LAYER_PASSAGES = 3;
    /** A lesson answer keeps at least this many passages from the lesson's key text. */
    private static final int MIN_KEY_TEXT_PASSAGES = 2;
    private static final int MAX_KEY_TEXT_CHUNKS = 12;

    private final VectorStores vectorStores;
    private final Bm25Store bm25Store;
    private final PassageReranker reranker;
    private final QueryExpander queryExpander;
    private final CorpusReleaseStore releases;
    private final int topK;
    private final double similarityThreshold;
    private final int answerChunks;
    private final int contextNeighbors;
    private final int contextMaxChars;
    private final ExecutorService searchPool; // null: searches run one after another
    private final long searchTimeoutMs;

    public TripitakaRetriever(VectorStores vectorStores, Bm25Store bm25Store, PassageReranker reranker,
                              QueryExpander queryExpander, CorpusReleaseStore releases,
                              @Value("${rag.top-k:10}") int topK,
                              @Value("${rag.similarity-threshold:0.25}") double similarityThreshold,
                              @Value("${rag.answer-chunks:6}") int answerChunks,
                              @Value("${rag.context.neighbors:1}") int contextNeighbors,
                              @Value("${rag.context.max-chars:30000}") int contextMaxChars,
                              @Value("${rag.search.threads:8}") int searchThreads,
                              @Value("${rag.search.timeout-ms:20000}") long searchTimeoutMs) {
        this.vectorStores = vectorStores;
        this.bm25Store = bm25Store;
        this.reranker = reranker;
        this.queryExpander = queryExpander;
        this.releases = releases;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.answerChunks = answerChunks;
        this.contextNeighbors = contextNeighbors;
        this.contextMaxChars = contextMaxChars;
        this.searchTimeoutMs = searchTimeoutMs;
        this.searchPool = searchThreads <= 1 ? null : Executors.newFixedThreadPool(searchThreads, runnable -> {
            var thread = new Thread(runnable, "retrieval-search");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Adds the chunks around each hit (same sutta, same release) so the model sees the wider teaching. */
    public List<Document> withContext(List<Document> hits) {
        if (hits.isEmpty() || contextNeighbors <= 0) {
            return ContextExpander.expand(hits, hit -> List.of(), 0, contextMaxChars);
        }
        // Each hit's neighbours come from its own collection and release (hits may span several layers).
        List<Document> expanded = ContextExpander.expand(hits, hit -> bm25Store.chunksOfText(
                        String.valueOf(hit.getMetadata().get("collection")),
                        String.valueOf(hit.getMetadata().get("release")),
                        String.valueOf(hit.getMetadata().get("textId"))),
                contextNeighbors, contextMaxChars);
        log.info("Context hits={} expanded={} chars={}", hits.size(), expanded.size(),
                expanded.stream().mapToInt(d -> d.getText().length()).sum());
        return expanded;
    }

    public List<Document> search(String query, String collection) {
        return search(query, List.of(collection));
    }

    /**
     * Searches several collections at once (e.g. suttas + Abhidhamma + commentaries). The first collection is the
     * primary source: its top passages are always kept, and the other layers only add passages that score as well.
     */
    public List<Document> search(String query, List<String> collections) {
        return searchQueries(queryExpander.expand(query), collections);
    }

    /** Like {@link #search(String, List)} for searches already planned (see {@link QueryExpander#plan}). */
    public List<Document> searchQueries(List<String> queries, List<String> collections) {
        return searchQueries(queries, collections, Set.of());
    }

    /**
     * @param keyTextIds texts of the primary collection that must be in the answer (a lesson's key text, e.g.
     *                   "mn135"): their chunks join the candidates, and at least {@link #MIN_KEY_TEXT_PASSAGES} are
     *                   kept even if other passages rank higher. Empty for a free question.
     */
    public List<Document> searchQueries(List<String> queries, List<String> collections, Set<String> keyTextIds) {
        String primary = collections.get(0);
        Map<String, String> published = new java.util.LinkedHashMap<>();
        for (String collection : collections) {
            releases.current(collection).ifPresentOrElse(release -> published.put(collection, release),
                    () -> log.warn("No published release for collection={}; skipping it", collection));
        }
        if (published.isEmpty()) {
            return List.of();
        }

        long started = System.nanoTime();
        try {
            vectorStores.prepareQueries(queries); // one embedding call for all queries x collections
        } catch (RuntimeException failure) {
            log.warn("Batch query embedding failed; each search embeds its own query errorType={}",
                    failure.getClass().getSimpleName());
        }
        // Every expanded query runs both searches in every collection, in parallel. Results are collected in
        // submission order (collection, query, vector-then-BM25), so fusion sees the same rankings as a
        // sequential run. The primary (sutta) collection and the other layers are fused separately, so dense
        // commentary can never push the suttas out of the pool.
        long searchStarted = System.nanoTime();
        var filters = new FilterExpressionBuilder();
        List<Search> searches = new ArrayList<>();
        for (var entry : published.entrySet()) {
            String collection = entry.getKey();
            String release = entry.getValue();
            var filter = filters.and(filters.eq("collection", collection), filters.eq("release", release)).build();
            for (String q : queries) {
                searches.add(new Search(collection, true, () -> vectorStores.forCollection(collection)
                        .similaritySearch(SearchRequest.builder()
                                .query(q)
                                .topK(topK)
                                .similarityThreshold(similarityThreshold)
                                .filterExpression(filter)
                                .build())));
                searches.add(new Search(collection, false, () -> bm25Store.search(q, collection, release, topK)));
            }
        }
        List<List<Document>> results = runAll(searches);

        List<List<Document>> rankings = new ArrayList<>();
        List<List<Document>> otherRankings = new ArrayList<>();
        Set<String> semanticIds = new java.util.HashSet<>();
        Set<String> keywordIds = new java.util.HashSet<>();
        for (int i = 0; i < searches.size(); i++) {
            Search search = searches.get(i);
            List<Document> found = usable(results.get(i));
            (search.collection().equals(primary) ? rankings : otherRankings).add(found);
            found.forEach(d -> (search.semantic() ? semanticIds : keywordIds).add(d.getId()));
        }
        long searchMs = (System.nanoTime() - searchStarted) / 1_000_000;

        boolean layered = !otherRankings.isEmpty() && !rankings.isEmpty();
        // The reranker sees the question plus its expansions: a description ("the origin of the world") only
        // reaches the right sutta if the reranker also knows the name the expansion found ("Aggañña Sutta").
        String rerankQuery = String.join("\n", queries);
        // A lesson's key text joins the candidates (after the search results, so without a reranker it never
        // pushes them aside) and is guaranteed a place in the answer below.
        List<Document> keyChunks = keyTextChunks(primary, published.get(primary), keyTextIds);
        Set<String> keyIds = new java.util.HashSet<>(keyChunks.stream().map(Document::getId).toList());
        List<Document> merged;
        List<Document> reranked;
        List<Document> chosen;
        if (layered) {
            // Pool = best suttas + best of the other layers, ranked together in one rerank call; then every
            // sutta a suttas-only search would return is kept, plus only the layer passages that score as well.
            merged = new ArrayList<>(HybridResultRRFMerger.mergeAll(rankings, PRIMARY_POOL));
            merged.addAll(HybridResultRRFMerger.mergeAll(otherRankings, OTHER_POOL));
            merged = KeyTexts.appendNew(merged, keyChunks);
            reranked = reranker.rerank(rerankQuery, merged, merged.size());
            chosen = Layers.suttasPlusRelevantLayers(reranked, primary, answerChunks, MAX_LAYER_PASSAGES);
        } else {
            merged = KeyTexts.appendNew(
                    HybridResultRRFMerger.mergeAll(rankings.isEmpty() ? otherRankings : rankings, topK * 2), keyChunks);
            if (keyChunks.isEmpty()) {
                reranked = reranker.rerank(rerankQuery, merged, answerChunks);
                chosen = reranked;
            } else {
                reranked = reranker.rerank(rerankQuery, merged, merged.size());
                chosen = reranked.subList(0, Math.min(answerChunks, reranked.size()));
            }
        }
        List<Document> finalDocs = tagOrigins(semanticIds, keywordIds, keyIds,
                KeyTexts.ensure(chosen, reranked, keyTextIds, MIN_KEY_TEXT_PASSAGES));

        log.info("Retrieval collections={} queries={} searches={} semantic={} bm25={} merged={} final={} searchMs={} "
                        + "retrievalMs={} texts={}",
                published.keySet(), queries.size(), searches.size(), semanticIds.size(), keywordIds.size(), merged.size(),
                finalDocs.size(), searchMs, (System.nanoTime() - started) / 1_000_000,
                finalDocs.stream().map(d -> String.valueOf(d.getMetadata().get("textId"))).toList());
        return finalDocs;
    }

    /** One vector or BM25 search of one query in one collection. */
    private record Search(String collection, boolean semantic, Callable<List<Document>> call) {
    }

    /**
     * Runs the searches on the bounded pool (or inline with one thread) and returns their results in the order
     * given. A search that fails or misses the request deadline counts as empty, like a collection with no hits.
     */
    private List<List<Document>> runAll(List<Search> searches) {
        List<List<Document>> results = new ArrayList<>();
        if (searchPool == null) {
            for (Search search : searches) {
                results.add(runOne(search));
            }
            return results;
        }
        List<Future<List<Document>>> futures = searches.stream()
                .map(search -> searchPool.submit(() -> runOne(search)))
                .toList();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(searchTimeoutMs);
        for (int i = 0; i < futures.size(); i++) {
            Future<List<Document>> future = futures.get(i);
            try {
                results.add(future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
            } catch (TimeoutException late) {
                future.cancel(true);
                log.warn("Search timed out collection={} semantic={} timeoutMs={}",
                        searches.get(i).collection(), searches.get(i).semantic(), searchTimeoutMs);
                results.add(List.of());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                futures.forEach(f -> f.cancel(true));
                throw new IllegalStateException("Retrieval interrupted", interrupted);
            } catch (ExecutionException failure) {
                // runOne already catches search failures; anything here is unexpected.
                log.warn("Search failed errorType={}", failure.getCause().getClass().getSimpleName());
                results.add(List.of());
            }
        }
        return results;
    }

    private static List<Document> runOne(Search search) {
        try {
            List<Document> found = search.call().call();
            return found == null ? List.of() : found;
        } catch (Exception failure) {
            log.warn("Search failed collection={} semantic={} errorType={}",
                    search.collection(), search.semantic(), failure.getClass().getSimpleName());
            return List.of();
        }
    }

    @PreDestroy
    void shutdown() {
        if (searchPool != null) {
            searchPool.shutdownNow();
        }
    }

    /** The first chunks of each key text in the primary collection (at most {@link #MAX_KEY_TEXT_CHUNKS} in all). */
    private List<Document> keyTextChunks(String collection, String release, Set<String> keyTextIds) {
        if (keyTextIds.isEmpty() || release == null) {
            return List.of();
        }
        List<Document> chunks = new ArrayList<>();
        for (String textId : keyTextIds.stream().sorted().toList()) {
            try {
                chunks.addAll(usable(bm25Store.chunksOfText(collection, release, textId)));
            } catch (RuntimeException failure) {
                log.warn("Key text lookup failed textId={} errorType={}", textId, failure.getClass().getSimpleName());
            }
        }
        if (chunks.isEmpty()) {
            log.warn("Key texts {} not found in collection={}", keyTextIds, collection);
        }
        return chunks.stream().limit(MAX_KEY_TEXT_CHUNKS).toList();
    }

    private static List<Document> tagOrigins(Set<String> semanticIds, Set<String> keywordIds, Set<String> keyIds,
                                             List<Document> docs) {
        return docs.stream().map(document -> {
            var origins = new ArrayList<String>();
            if (semanticIds.contains(document.getId())) {
                origins.add("semantic");
            }
            if (keywordIds.contains(document.getId())) {
                origins.add("bm25");
            }
            if (keyIds.contains(document.getId())) {
                origins.add("keyText");
            }
            var metadata = new HashMap<>(document.getMetadata());
            metadata.put("retrievedBy", origins);
            return document.mutate().metadata(metadata).build();
        }).toList();
    }

    private static List<Document> usable(List<Document> documents) {
        List<Document> kept = documents.stream()
                .filter(d -> d.getText() != null && !d.getText().isBlank() && d.getText().length() <= MAX_CHUNK_CHARS)
                .toList();
        if (kept.size() < documents.size()) {
            log.warn("Dropped {} unusable chunks (empty or over {} chars)", documents.size() - kept.size(), MAX_CHUNK_CHARS);
        }
        return kept;
    }
}
