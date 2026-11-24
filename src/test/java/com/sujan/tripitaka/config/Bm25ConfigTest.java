package com.sujan.tripitaka.config;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Bm25ConfigTest {

    private static List<String> tokens(String text) throws Exception {
        try (Analyzer analyzer = Bm25Config.paliFriendlyAnalyzer();
             var stream = analyzer.tokenStream("content", text)) {
            var term = stream.addAttribute(CharTermAttribute.class);
            var out = new ArrayList<String>();
            stream.reset();
            while (stream.incrementToken()) {
                out.add(term.toString());
            }
            stream.end();
            return out;
        }
    }

    @Test
    void sinhalaMatchesWithOrWithoutZeroWidthJoiner() throws Exception {
        assertEquals(tokens("බ්රහ්මජාල සූත්රය"), tokens("බ්‍රහ්මජාල සූත්‍රය"));
        assertEquals(2, tokens("බ්‍රහ්මජාල සූත්‍රය").size());
    }

    @Test
    void paliStillFoldsDiacritics() throws Exception {
        assertEquals(List.of("nibbana"), tokens("Nibbāna"));
    }
}
