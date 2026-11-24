package com.sujan.tripitaka.ingest;

import com.sujan.tripitaka.model.SourceText;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads PDFs page by page; each page becomes a passage referenced as "p.N".
 * Caution: many Sinhala Tripitaka PDFs use legacy (non-Unicode) fonts and extract as garbage.
 * Check the first extracted pages before ingesting a whole volume (see README).
 */
public class PdfReader {

    private final String nikaya;

    public PdfReader(String nikaya) {
        this.nikaya = nikaya == null || nikaya.isBlank() ? "Other" : nikaya;
    }

    public List<SourceText> read(Path root) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(path -> path.toString().toLowerCase().endsWith(".pdf")).sorted().toList();
        }
        List<SourceText> texts = new ArrayList<>();
        for (Path file : files) {
            texts.add(readOne(file));
        }
        return texts;
    }

    private SourceText readOne(Path file) {
        String fileName = file.getFileName().toString();
        String textId = fileName.replaceAll("(?i)\\.pdf$", "").toLowerCase().replaceAll("[^\\p{L}\\p{M}\\p{N}]+", "-");

        List<SourceText.Passage> passages = new ArrayList<>();
        List<Document> pages = new PagePdfDocumentReader(new FileSystemResource(file)).get();
        for (int i = 0; i < pages.size(); i++) {
            Document page = pages.get(i);
            String text = page.getText() == null ? "" : page.getText().replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) {
                continue;
            }
            Object pageNumber = page.getMetadata().getOrDefault("page_number", i + 1);
            passages.add(new SourceText.Passage("p." + pageNumber, text));
        }
        return new SourceText(textId, fileName.replaceAll("(?i)\\.pdf$", ""), nikaya, fileName, passages);
    }
}
