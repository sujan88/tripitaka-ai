package com.sujan.tripitaka.config;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilterFactory;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.pattern.PatternReplaceCharFilterFactory;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;

@Configuration
public class Bm25Config {

    /**
     * Pali terms appear with and without diacritics ("nibbāna" / "nibbana").
     * ASCII folding makes both spellings match the same token.
     */
    public static Analyzer paliFriendlyAnalyzer() throws IOException {
        return CustomAnalyzer.builder()
                // Sinhala writes some letters with or without zero-width joiners (බ්‍ර / බ්ර): drop them so both match.
                .addCharFilter(PatternReplaceCharFilterFactory.class, "pattern", "[\\u200C\\u200D]", "replacement", "")
                .withTokenizer(StandardTokenizerFactory.class)
                .addTokenFilter(LowerCaseFilterFactory.class)
                .addTokenFilter(ASCIIFoldingFilterFactory.class)
                .build();
    }

    @Bean(destroyMethod = "close")
    public Directory bm25Directory(@Value("${rag.bm25.path:./data/bm25}") String path) throws IOException {
        return FSDirectory.open(Path.of(path));
    }

    @Bean(destroyMethod = "close")
    public Analyzer bm25Analyzer() throws IOException {
        return paliFriendlyAnalyzer();
    }

    @Bean(destroyMethod = "close")
    public IndexWriter bm25Writer(Directory bm25Directory, Analyzer bm25Analyzer) throws IOException {
        var config = new IndexWriterConfig(bm25Analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        config.setSimilarity(new BM25Similarity());
        return new IndexWriter(bm25Directory, config);
    }

    @Bean(destroyMethod = "close")
    public SearcherManager bm25Searchers(IndexWriter bm25Writer) throws IOException {
        return new SearcherManager(bm25Writer, new SearcherFactory());
    }
}
