package com.sujan.tripitaka.ingest;

import com.sujan.tripitaka.model.SourceText;
import org.springframework.ai.document.Document;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Packs whole passages (paragraphs/pages) into chunks of about maxChars, never across texts.
 * Unlike a fixed token splitter, a teaching stays together and each chunk keeps a real reference.
 */
public class PassageChunker {

    /** Part of the release hash: changing chunking produces a new release. */
    public static final String VERSION = "passages-2400-v1";

    private final int maxChars;

    public PassageChunker(int maxChars) {
        if (maxChars < 200) {
            throw new IllegalArgumentException("maxChars too small");
        }
        this.maxChars = maxChars;
    }

    /** Wraps a search header so it can be told apart from scripture (and stripped from excerpts). */
    public static String header(String searchHeader) {
        return HEADER_OPEN + searchHeader + HEADER_CLOSE;
    }

    /** Removes a leading search header, if any. */
    public static String withoutHeader(String text) {
        return text == null ? null : HEADER.matcher(text).replaceFirst("");
    }

    public static final String HEADER_OPEN = "⟨";
    public static final String HEADER_CLOSE = "⟩";
    private static final java.util.regex.Pattern HEADER = java.util.regex.Pattern.compile("^⟨[^⟩]*⟩\\s*");

    public List<Document> chunk(String collection, String release, SourceText text) {
        List<Document> chunks = new ArrayList<>();
        List<SourceText.Passage> current = new ArrayList<>();
        int size = 0;
        int budget = text.searchHeader() == null ? maxChars : Math.max(maxChars / 2, maxChars - header(text.searchHeader()).length());

        for (SourceText.Passage passage : splitOversized(text.passages())) {
            if (size > 0 && size + passage.text().length() > budget) {
                chunks.add(toDocument(collection, release, text, current, chunks.size()));
                current = new ArrayList<>();
                size = 0;
            }
            current.add(passage);
            size += passage.text().length() + 1;
        }
        if (!current.isEmpty()) {
            chunks.add(toDocument(collection, release, text, current, chunks.size()));
        }
        return chunks;
    }

    private Document toDocument(String collection, String release, SourceText text,
                                List<SourceText.Passage> passages, int index) {
        String body = String.join("\n\n", passages.stream().map(SourceText.Passage::text).toList());
        if (text.searchHeader() != null) {
            body = header(text.searchHeader()) + "\n\n" + body;
        }
        String first = passages.get(0).reference();
        String last = passages.get(passages.size() - 1).reference();
        String reference = first.equals(last) ? first : first + " … " + last;

        // Release is part of the id: a new release never overwrites points an older release still serves.
        String identity = collection + ":" + release + ":" + text.textId() + ":chunk:" + index;
        String id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("collection", collection);
        metadata.put("release", release);
        metadata.put("textId", text.textId());
        metadata.put("title", text.title());
        metadata.put("nikaya", text.nikaya());
        metadata.put("source", text.source());
        metadata.put("reference", reference);
        metadata.put("chunkIndex", index);

        return Document.builder().id(id).text(body).metadata(metadata).build();
    }

    /** A single passage longer than maxChars (common for PDF pages) is cut at sentence/space boundaries. */
    private List<SourceText.Passage> splitOversized(List<SourceText.Passage> passages) {
        List<SourceText.Passage> out = new ArrayList<>();
        for (SourceText.Passage passage : passages) {
            String remaining = passage.text();
            int part = 1;
            while (remaining.length() > maxChars) {
                int cut = remaining.lastIndexOf(". ", maxChars);
                if (cut < maxChars / 2) {
                    cut = remaining.lastIndexOf(' ', maxChars);
                }
                if (cut < maxChars / 2) {
                    cut = maxChars;
                }
                out.add(new SourceText.Passage(passage.reference() + " (" + part++ + ")",
                        remaining.substring(0, cut + 1).trim()));
                remaining = remaining.substring(cut + 1).trim();
            }
            if (!remaining.isEmpty()) {
                out.add(new SourceText.Passage(part == 1 ? passage.reference() : passage.reference() + " (" + part + ")",
                        remaining));
            }
        }
        return out;
    }
}
