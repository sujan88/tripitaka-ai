package com.sujan.tripitaka.ingest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SuttaCentralReaderTest {

    @Test
    void groupsSegmentsByParagraphAndReadsTitle() {
        String json = """
                {
                  "mn10:0.1": "Middle Discourses 10",
                  "mn10:0.2": "Mindfulness Meditation",
                  "mn10:1.1": "So I have heard. ",
                  "mn10:1.2": "At one time the Buddha was staying in the land of the Kurus.",
                  "mn10:2.1": "Mendicants, the four kinds of mindfulness meditation",
                  "mn10:2.2": ""
                }
                """;

        var text = new SuttaCentralReader().parse("mn10_translation-en-sujato.json", json);

        assertEquals("mn10", text.textId());
        assertEquals("Mindfulness Meditation", text.title());
        assertEquals("Majjhima Nikāya", text.nikaya());
        assertEquals(2, text.passages().size());
        assertEquals("mn10:1.1–1.2", text.passages().get(0).reference());
        assertEquals("mn10:2.1", text.passages().get(1).reference());
        assertEquals("So I have heard. At one time the Buddha was staying in the land of the Kurus.",
                text.passages().get(0).text());
        assertEquals("MN 10 · Middle Discourses 10 · Mindfulness Meditation · Majjhima Nikāya", text.searchHeader());
    }

    @Test
    void headerAddsPaliTitlesFromTheRootFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var translation = dir.resolve("translation/en/sujato/sutta/sn/sn56");
        var root = dir.resolve("root/pli/ms/sutta/sn/sn56");
        java.nio.file.Files.createDirectories(translation);
        java.nio.file.Files.createDirectories(root);
        java.nio.file.Files.writeString(translation.resolve("sn56.11_translation-en-sujato.json"), """
                {"sn56.11:0.1": "Linked Discourses 56.11", "sn56.11:0.3": "Rolling Forth the Wheel of Dhamma",
                 "sn56.11:1.1": "At one time the Buddha was staying near Varanasi."}
                """);
        java.nio.file.Files.writeString(root.resolve("sn56.11_root-pli-ms.json"), """
                {"sn56.11:0.1": "Saṁyutta Nikāya 56.11", "sn56.11:0.3": "Dhammacakkappavattanasutta",
                 "sn56.11:1.1": "Ekaṁ samayaṁ bhagavā bārāṇasiyaṁ viharati."}
                """);

        var text = new SuttaCentralReader().read(dir.resolve("translation/en/sujato/sutta")).get(0);

        assertEquals("SN 56.11 · Linked Discourses 56.11 · Rolling Forth the Wheel of Dhamma · Saṁyutta Nikāya 56.11"
                + " · Dhammacakkappavattanasutta · Saṃyutta Nikāya", text.searchHeader());
        assertEquals(1, text.passages().size()); // the Pali root file is only used for titles
    }

    @Test
    void keepsThreePartParagraphsSeparate() {
        String json = """
                {"dn1:0.1": "Long Discourses 1", "dn1:0.2": "Brahma's Net",
                 "dn1:1.1.1": "So I have heard.", "dn1:1.1.2": "At one time.",
                 "dn1:1.2.1": "Next paragraph."}
                """;

        var text = new SuttaCentralReader().parse("dn1_translation-en-sujato.json", json);

        assertEquals(2, text.passages().size());
        assertEquals("dn1:1.1.1–1.1.2", text.passages().get(0).reference());
        assertEquals("dn1:1.2.1", text.passages().get(1).reference());
    }

    @Test
    void keepsSuttasInARangeFileSeparate() {
        String json = """
                {"an1.1:1.1": "First sutta.", "an1.2:1.1": "Second sutta."}
                """;

        var text = new SuttaCentralReader().parse("an1.1-10_translation-en-sujato.json", json);

        assertEquals(2, text.passages().size());
        assertEquals("an1.2:1.1", text.passages().get(1).reference());
    }

    @Test
    void mapsDottedTextIdsToNikaya() {
        assertEquals("Saṃyutta Nikāya", Nikayas.of("sn56.11"));
        assertEquals("Aṅguttara Nikāya", Nikayas.of("an3.65"));
        assertEquals("Khuddaka Nikāya", Nikayas.of("dhp1-20"));
    }
}
