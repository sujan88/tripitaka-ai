package com.sujan.tripitaka.ingest;

import com.sujan.tripitaka.model.SourceText;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads SuttaCentral bilara-data JSON (one file per sutta, e.g. mn10_translation-en-sujato.json).
 * Each file is a flat map of segment id to text: {"mn10:1.1": "So I have heard...", ...}.
 * Segments sharing a paragraph number ("mn10:12.x") become one passage; "x:0.y" segments are the title.
 */
public class SuttaCentralReader {

    private static final TypeReference<LinkedHashMap<String, String>> SEGMENTS = new TypeReference<>() {
    };

    private final JsonMapper json = JsonMapper.builder().build();

    public List<SourceText> read(Path root) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
        List<SourceText> texts = new ArrayList<>();
        for (Path file : files) {
            SourceText text = parse(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8),
                    paliTitles(file));
            if (!text.passages().isEmpty()) {
                texts.add(text);
            }
        }
        return texts;
    }

    /**
     * Pali titles from the matching root file, e.g. translation/en/sujato/.../sn56.11_translation-en-sujato.json
     * -> root/pli/ms/.../sn56.11_root-pli-ms.json ("Dhammacakkappavattanasutta"). Empty if it is not there.
     */
    private List<String> paliTitles(Path translationFile) throws IOException {
        String path = translationFile.toString();
        int marker = path.indexOf("/translation/");
        if (marker < 0) {
            return List.of();
        }
        String afterTranslation = path.substring(marker + "/translation/".length()); // en/sujato/sutta/...
        String[] parts = afterTranslation.split("/", 3);
        if (parts.length < 3) {
            return List.of();
        }
        Path root = Path.of(path.substring(0, marker), "root", "pli", "ms",
                parts[2].replaceAll("_translation-[^/]*\\.json$", "_root-pli-ms.json"));
        if (!Files.exists(root)) {
            return List.of();
        }
        Map<String, String> segments = json.readValue(Files.readString(root, StandardCharsets.UTF_8), SEGMENTS);
        return segments.entrySet().stream()
                .filter(e -> isTitleSegment(e.getKey()) && e.getValue() != null && !e.getValue().isBlank())
                .map(e -> e.getValue().trim())
                .toList();
    }

    SourceText parse(String fileName, String content) {
        return parse(fileName, content, List.of());
    }

    SourceText parse(String fileName, String content, List<String> paliTitles) {
        Map<String, String> segments = json.readValue(content, SEGMENTS);
        String textId = fileName.contains("_") ? fileName.substring(0, fileName.indexOf('_')) : fileName.replace(".json", "");

        List<String> titleParts = new ArrayList<>();
        List<SourceText.Passage> passages = new ArrayList<>();
        String currentParagraph = null;
        String firstSegment = null;
        String lastSegment = null;
        var buffer = new StringBuilder();

        for (var entry : segments.entrySet()) {
            String segmentId = entry.getKey();
            String text = entry.getValue() == null ? "" : entry.getValue().trim();
            String paragraph = paragraphOf(segmentId);

            if (isTitleSegment(segmentId)) {
                if (!text.isEmpty()) {
                    titleParts.add(text);
                }
                continue;
            }
            if (!paragraph.equals(currentParagraph) && buffer.length() > 0) {
                passages.add(new SourceText.Passage(range(firstSegment, lastSegment), buffer.toString().trim()));
                buffer.setLength(0);
                firstSegment = null;
            }
            currentParagraph = paragraph;
            if (text.isEmpty()) {
                continue;
            }
            if (firstSegment == null) {
                firstSegment = segmentId;
            }
            lastSegment = segmentId;
            buffer.append(text).append(' ');
        }
        if (buffer.length() > 0) {
            passages.add(new SourceText.Passage(range(firstSegment, lastSegment), buffer.toString().trim()));
        }

        String title = titleParts.isEmpty() ? textId : titleParts.get(titleParts.size() - 1);
        String nikaya = Nikayas.of(textId);
        return new SourceText(textId, title, nikaya, fileName, passages, searchHeader(textId, titleParts, paliTitles, nikaya));
    }

    /**
     * "SN 56.11 · Linked Discourses 56.11 · Rolling Forth the Wheel of Dhamma · Dhammacakkappavattanasutta · …":
     * the sutta number, English and Pali titles and the nikaya, so a search by any of these names finds the sutta.
     */
    static String searchHeader(String textId, List<String> englishTitles, List<String> paliTitles, String nikaya) {
        var names = new java.util.LinkedHashSet<String>();
        names.add(textId.replaceFirst("^([a-z]+)", "$1 ").toUpperCase().replace("  ", " "));
        englishTitles.forEach(names::add);
        paliTitles.forEach(names::add);
        names.add(nikaya);
        names.removeIf(n -> n == null || n.isBlank());
        return String.join(" · ", names);
    }

    /**
     * The segment id without its last number, so it stays unique across a file:
     * "mn10:12.3" -> "mn10:12"; "dn1:1.2.3" -> "dn1:1.2"; "an1.2:1.1" -> "an1.2:1" (range files hold many suttas).
     */
    private static String paragraphOf(String segmentId) {
        int colon = segmentId.indexOf(':');
        int dot = segmentId.lastIndexOf('.');
        return dot > colon ? segmentId.substring(0, dot) : segmentId;
    }

    private static boolean isTitleSegment(String segmentId) {
        String local = segmentId.substring(segmentId.indexOf(':') + 1);
        return local.equals("0") || local.startsWith("0.");
    }

    private static String range(String first, String last) {
        if (first == null) {
            return "";
        }
        if (first.equals(last)) {
            return first;
        }
        return first + "–" + last.substring(last.indexOf(':') + 1);
    }
}
