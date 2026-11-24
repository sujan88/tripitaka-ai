package com.sujan.tripitaka.ingest;

import com.sujan.tripitaka.model.SourceText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the Buddha Jayanthi Tripitaka JSON from github.com/pathnirvana/tipitaka.lk
 * (public/static/text/*.json, CC BY-ND 4.0: text is kept verbatim apart from markup).
 *
 * <p>One file is a book: pages with parallel "pali" and "sinh" entry lists. Section keys follow the
 * site's own build/tree.ts, so a key like "dn-1-1" is also the page https://tipitaka.lk/dn-1-1.
 * Each leaf section (a sutta) becomes one SourceText of Sinhala passages; tiny numbered suttas
 * ("1. 1. 1.") are grouped under their parent section so chunks stay meaningful.
 */
public class TipitakaLkReader {

    private static final Logger log = LoggerFactory.getLogger(TipitakaLkReader.class);

    /** Sinhala names of the five nikayas of the Sutta Pitaka. */
    static final Map<String, String> NIKAYAS = Map.of(
            "dn", "දීඝ නිකාය",
            "mn", "මජ්ඣිම නිකාය",
            "sn", "සංයුත්ත නිකාය",
            "an", "අඞ්ගුත්තර නිකාය",
            "kn", "ඛුද්දක නිකාය");
    private static final Pattern BOOK_FILE = Pattern.compile("^(dn|mn|sn|an|kn)-[a-z\\d-]+$");
    /** Books whose level-1 heading comes after its verses (tipitaka.lk build/tree.ts headingAtEndKeys). */
    private static final List<Pattern> HEADING_AT_END = List.of("kn-vv", "kn-pv", "kn-thag", "kn-thig", "kn-jat$",
            "kn-jat-(5|11|22)").stream().map(Pattern::compile).toList();
    private static final Pattern NUMERIC = Pattern.compile("^[\\d. \\-]+$");
    private static final int NIKAYA_LEVEL = 6;

    private final List<String> includes;
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    record Entry(String type, String text, Integer level, Object keyOffset) {
    }

    record Language(List<Entry> entries) {
    }

    record Page(Integer pageNum, Language pali, Language sinh) {
    }

    record Book(String filename, List<Page> pages) {
    }

    /** @param includes file-name prefixes to read, e.g. ["dn-"] or ["dn-1"]; empty means all five nikayas */
    public TipitakaLkReader(List<String> includes) {
        this.includes = includes == null ? List.of() : includes;
    }

    public List<SourceText> read(Path directory) throws IOException {
        List<Path> files;
        try (Stream<Path> list = Files.list(directory)) {
            files = list.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(p -> BOOK_FILE.matcher(baseName(p)).matches())
                    .filter(p -> includes.isEmpty() || includes.stream().anyMatch(baseName(p)::startsWith))
                    // Parents before children ("dn-1" before "dn-1-3"), as the site's tree builder expects.
                    .sorted((a, b) -> baseName(a).compareTo(baseName(b)))
                    .toList();
        }
        var tree = new Tree();
        Map<String, Section> sections = new LinkedHashMap<>();
        for (Path file : files) {
            readBook(json.readValue(file.toFile(), Book.class), file.getFileName().toString(), tree, sections);
        }
        return sections.values().stream()
                .filter(s -> !s.passages.isEmpty())
                .map(s -> {
                    String nikaya = NIKAYAS.get(s.key.split("-")[0]);
                    return new SourceText(s.key, s.title, nikaya, s.source, s.passages, searchHeader(tree, s.key, nikaya));
                })
                .toList();
    }

    /**
     * Sinhala title, Pali title, enclosing section (vagga / saṃyutta) and nikaya, so that a question naming the
     * sutta, its Pali form or its section finds every chunk of it. Spelling variants of ṅ (ඞ්ග / ංග) are added.
     */
    static String searchHeader(Tree tree, String key, String nikaya) {
        var names = new java.util.LinkedHashSet<String>();
        names.add(tree.names.get(key));
        names.add(tree.paliNames.get(key));
        // Two levels up: the vagga, and the saṃyutta / nipāta / book that holds it.
        String parent = tree.parents.get(key);
        for (int up = 0; up < 2 && parent != null && tree.names.containsKey(parent); up++) {
            names.add(tree.names.get(parent));
            names.add(tree.paliNames.get(parent));
            parent = tree.parents.get(parent);
        }
        for (String name : List.copyOf(names)) {
            if (name != null && name.contains("ඞ්ග")) {
                names.add(name.replace("ඞ්ග", "ංග"));
            }
        }
        names.add(nikaya);
        names.removeIf(n -> n == null || n.isBlank() || NUMERIC.matcher(n).matches());
        return String.join(" · ", names);
    }

    private static String baseName(Path path) {
        return path.getFileName().toString().replaceAll("\\.json$", "");
    }

    private static final class Section {
        final String key;
        final String title;
        final String source;
        final List<SourceText.Passage> passages = new ArrayList<>();

        Section(String key, String title, String source) {
            this.key = key;
            this.title = title;
            this.source = source;
        }
    }

    /** Key and level bookkeeping shared across files, ported from tipitaka.lk build/tree.ts. */
    static final class Tree {
        final Map<String, Integer> levels = new HashMap<>(Map.of("dn", NIKAYA_LEVEL, "mn", NIKAYA_LEVEL,
                "sn", NIKAYA_LEVEL, "an", NIKAYA_LEVEL, "kn", NIKAYA_LEVEL));
        final Map<String, String> names = new HashMap<>();
        final Map<String, String> paliNames = new HashMap<>();
        final Map<String, String> parents = new HashMap<>();
    }

    private record Located(Entry entry, int pageNum) {
    }

    private void readBook(Book book, String fileName, Tree tree, Map<String, Section> sections) {
        String fileKey = book.filename() == null ? fileName.replaceAll("\\.json$", "") : book.filename();
        List<Located> pali = flatten(book, true);
        List<Located> sinh = flatten(book, false);
        List<Integer> paliHeadings = headingIndexes(pali);
        List<Integer> sinhHeadings = headingIndexes(sinh);
        if (paliHeadings.size() != sinhHeadings.size()) {
            log.warn("Skipping {}: pali and sinhala headings differ ({} vs {})", fileKey, paliHeadings.size(), sinhHeadings.size());
            return;
        }

        String[] parts = fileKey.split("-");
        Deque<String[]> stack = new ArrayDeque<>();
        stack.push(new String[]{String.join("-", List.of(parts).subList(0, parts.length - 1)), parts[parts.length - 1]});

        List<String> keys = new ArrayList<>();
        List<String> parents = new ArrayList<>();
        List<Integer> levels = new ArrayList<>();
        for (int h = 0; h < paliHeadings.size(); h++) {
            Entry heading = pali.get(paliHeadings.get(h)).entry();
            int level = heading.level() == null ? 0 : heading.level();
            while (stack.size() > 1 && tree.levels.containsKey(stack.peek()[0]) && tree.levels.get(stack.peek()[0]) <= level) {
                stack.pop();
            }
            String[] parent = stack.peek();
            if (heading.keyOffset() != null) {
                parent[1] = String.valueOf(heading.keyOffset());
            }
            String key = parent[0] + "-" + parent[1];
            parent[1] = increment(parent[1]);
            tree.levels.put(key, level);
            tree.names.put(key, name(sinh.get(sinhHeadings.get(h)).entry().text()));
            tree.paliNames.put(key, name(heading.text()));
            tree.parents.put(key, parent[0]);
            keys.add(key);
            parents.add(parent[0]);
            levels.add(level);
            stack.push(new String[]{key, "1"});
        }

        boolean headingAtEnd = HEADING_AT_END.stream().anyMatch(p -> p.matcher(fileKey).find());
        for (int h = 0; h < sinhHeadings.size(); h++) {
            boolean leaf = h + 1 == sinhHeadings.size() || levels.get(h + 1) >= levels.get(h);
            if (!leaf) {
                continue;
            }
            String key = keys.get(h);
            String title = tree.names.get(key);
            String groupKey = key;
            if (NUMERIC.matcher(title).matches() && tree.names.containsKey(parents.get(h))) {
                groupKey = parents.get(h);
                title = tree.names.get(groupKey);
            }
            String sectionTitle = title;
            Section section = sections.computeIfAbsent(groupKey, k -> new Section(k, sectionTitle, fileName));
            // Normally a section's text follows its heading; in heading-at-end books a level-1 heading
            // closes the verses written since the previous heading.
            boolean closesSection = headingAtEnd && levels.get(h) == 1;
            int start = closesSection ? (h == 0 ? 0 : sinhHeadings.get(h - 1) + 1) : sinhHeadings.get(h) + 1;
            int end = closesSection ? sinhHeadings.get(h)
                    : h + 1 < sinhHeadings.size() ? sinhHeadings.get(h + 1) : sinh.size();
            for (int i = start; i < end; i++) {
                Located located = sinh.get(i);
                String text = clean(located.entry().text());
                if (!text.isEmpty()) {
                    section.passages.add(new SourceText.Passage(key + " · පිටුව " + located.pageNum(), text));
                }
            }
        }
    }

    private static List<Located> flatten(Book book, boolean pali) {
        List<Located> out = new ArrayList<>();
        List<Page> pages = book.pages() == null ? List.of() : book.pages();
        for (int p = 0; p < pages.size(); p++) {
            Page page = pages.get(p);
            Language language = pali ? page.pali() : page.sinh();
            if (language == null || language.entries() == null) {
                continue;
            }
            int pageNum = page.pageNum() == null ? p + 1 : page.pageNum();
            language.entries().forEach(e -> out.add(new Located(e, pageNum)));
        }
        return out;
    }

    private static List<Integer> headingIndexes(List<Located> entries) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if ("heading".equals(entries.get(i).entry().type())) {
                out.add(i);
            }
        }
        return out;
    }

    private static String increment(String offset) {
        try {
            return String.valueOf(Integer.parseInt(offset) + 1);
        } catch (NumberFormatException notNumeric) {
            return "NaN"; // same as the site's tree builder for non-numeric offsets
        }
    }

    /** Heading name as the site shows it (tree.ts getName). */
    static String name(String text) {
        String name = text == null ? "" : text.trim()
                .replaceAll("\\{.*?\\}", "")
                .replaceAll("[\\[\\]()]", "")
                .replaceAll("\\$\\$|\\*\\*|__", "");
        return name.replaceAll("\\.$", "").trim();
    }

    /** Removes the site's markup (footnote markers, bold/underline markers); the words stay verbatim. */
    static String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\{[^}]*\\}", "")
                .replaceAll("\\$\\$|\\*\\*|__", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
