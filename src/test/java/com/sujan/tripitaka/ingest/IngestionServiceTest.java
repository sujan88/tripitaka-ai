package com.sujan.tripitaka.ingest;

import com.sujan.tripitaka.model.SourceText;
import com.sujan.tripitaka.rag.CorpusReleaseStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IngestionServiceTest {

    @Test
    void refusesToPublishAnEmptyRelease() {
        var service = new IngestionService(null, null, new NoReleaseStore());
        var scannedPdf = List.of(new SourceText("scan", "Scan", "Other", "scan.pdf", List.of()));

        assertThrows(IllegalArgumentException.class, () -> service.ingest("sutta-en", scannedPdf));
    }

    @Test
    void rejectsDuplicateTextIds() {
        var service = new IngestionService(null, null, new NoReleaseStore());
        var text = new SourceText("mn1", "Root", "Majjhima Nikāya", "mn1.json",
                List.of(new SourceText.Passage("mn1:1", "So I have heard.")));

        assertThrows(IllegalArgumentException.class, () -> service.ingest("sutta-en", List.of(text, text)));
    }

    private static final class NoReleaseStore extends CorpusReleaseStore {
        NoReleaseStore() {
            super(null);
        }

        @Override
        public Optional<String> current(String collection) {
            return Optional.empty();
        }
    }

    @Test
    void releaseIdChangesOnlyWhenContentChanges() {
        var original = List.of(new SourceText("mn1", "Root", "Majjhima Nikāya", "mn1.json",
                List.of(new SourceText.Passage("mn1:1", "So I have heard."))));
        var edited = List.of(new SourceText("mn1", "Root", "Majjhima Nikāya", "mn1.json",
                List.of(new SourceText.Passage("mn1:1", "Thus have I heard."))));

        assertEquals(IngestionService.releaseOf("sutta-en", original), IngestionService.releaseOf("sutta-en", original));
        assertNotEquals(IngestionService.releaseOf("sutta-en", original), IngestionService.releaseOf("sutta-en", edited));
    }
}
