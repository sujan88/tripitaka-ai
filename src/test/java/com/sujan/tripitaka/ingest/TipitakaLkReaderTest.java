package com.sujan.tripitaka.ingest;

import com.sujan.tripitaka.model.SourceText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TipitakaLkReaderTest {

    @TempDir
    Path dir;

    private static String entry(String type, String text, Integer level) {
        return "{\"type\":\"" + type + "\",\"text\":\"" + text + "\"" + (level == null ? "" : ",\"level\":" + level) + "}";
    }

    /** One page with the same structure on both sides; Sinhala text differs so we can tell them apart. */
    private static String page(int pageNum, List<String> pali, List<String> sinh) {
        return "{\"pageNum\":" + pageNum + ",\"pali\":{\"entries\":[" + String.join(",", pali) + "],\"footnotes\":[]},"
                + "\"sinh\":{\"entries\":[" + String.join(",", sinh) + "],\"footnotes\":[]}}";
    }

    @Test
    void sectionKeysFollowTheSiteAndTextIsSinhala() throws Exception {
        // Like dn-1.json: a vagga (level 5) holding two suttas (level 3).
        String book = "{\"filename\":\"dn-1\",\"pages\":[" + page(2,
                List.of(entry("heading", "සීලක්ඛන්ධවග්ගො", 5), entry("heading", "බ්රහ්මජාලසුත්තං", 3),
                        entry("paragraph", "pali one", null), entry("heading", "සාමඤ්ඤඵලසුත්තං", 3),
                        entry("paragraph", "pali two", null)),
                List.of(entry("heading", "සීලස්කන්ධ වර්ගය", 5), entry("heading", "බ්‍රහ්මජාල සූත්‍රය{1}", 3),
                        entry("paragraph", "**මා විසින්** අසන ලදී.{2}", null), entry("heading", "සාමඤ්ඤඵල සූත්‍රය", 3),
                        entry("paragraph", "දෙවන සූත්‍රය", null))) + "]}";
        Files.writeString(dir.resolve("dn-1.json"), book);
        Files.writeString(dir.resolve("kn-dhp.json"), "{}"); // other books are ignored

        List<SourceText> texts = new TipitakaLkReader(List.of()).read(dir);

        assertEquals(List.of("dn-1-1", "dn-1-2"), texts.stream().map(SourceText::textId).toList());
        SourceText first = texts.get(0);
        assertEquals("බ්‍රහ්මජාල සූත්‍රය", first.title());
        assertEquals("දීඝ නිකාය", first.nikaya());
        assertEquals("dn-1-1 · පිටුව 2", first.passages().get(0).reference());
        assertEquals("මා විසින් අසන ලදී.", first.passages().get(0).text());
        // Sinhala and Pali titles, the vagga, and the nikaya, so a search by any of these names finds it.
        assertEquals("බ්‍රහ්මජාල සූත්‍රය · බ්රහ්මජාලසුත්තං · සීලස්කන්ධ වර්ගය · සීලක්ඛන්ධවග්ගො · දීඝ නිකාය",
                first.searchHeader());
    }

    @Test
    void groupsNumberedSuttasUnderTheirSection() throws Exception {
        // Like an-1.json: nipata (4) > vagga (3) > numbered suttas (1).
        String book = "{\"filename\":\"an-1\",\"pages\":[" + page(2,
                List.of(entry("heading", "1. එකක නිපාතො", 4), entry("heading", "1. චිත්තපරියාදානවග්ගො", 3),
                        entry("heading", "1. 1. 1.", 1), entry("paragraph", "a", null),
                        entry("heading", "1. 1. 2.", 1), entry("paragraph", "b", null)),
                List.of(entry("heading", "1. ඒකකනිපාතය", 4), entry("heading", "1. චිත්තපරියාදාන වර්ගය", 3),
                        entry("heading", "1. 1. 1.", 1), entry("paragraph", "පළමු", null),
                        entry("heading", "1. 1. 2.", 1), entry("paragraph", "දෙවන", null))) + "]}";
        Files.writeString(dir.resolve("an-1.json"), book);

        List<SourceText> texts = new TipitakaLkReader(List.of("an-")).read(dir);

        assertEquals(1, texts.size());
        assertEquals("an-1-1", texts.get(0).textId());
        assertEquals("1. චිත්තපරියාදාන වර්ගය", texts.get(0).title());
        assertEquals(List.of("an-1-1-1 · පිටුව 2", "an-1-1-2 · පිටුව 2"),
                texts.get(0).passages().stream().map(SourceText.Passage::reference).toList());
    }

    @Test
    void khuddakaBookKeysUseTheBookName() throws Exception {
        // Like kn-dhp.json: book heading (4) then chapters (1); the file name carries a non-numeric offset.
        String book = "{\"filename\":\"kn-dhp\",\"pages\":[" + page(2,
                List.of(entry("heading", "ධම්මපදපාළි", 4), entry("heading", "1. යමකවග්ගො", 1),
                        entry("paragraph", "p", null), entry("heading", "2. අප්පමාදවග්ගො", 1), entry("paragraph", "p", null)),
                List.of(entry("heading", "දම්පියාපෙළ", 4), entry("heading", "1. යමක වර්ගය", 1),
                        entry("paragraph", "පළමු", null), entry("heading", "2. අප්‍රමාද වර්ගය", 1),
                        entry("paragraph", "දෙවන", null))) + "]}";
        Files.writeString(dir.resolve("kn-dhp.json"), book);

        List<SourceText> texts = new TipitakaLkReader(List.of("kn-")).read(dir);

        assertEquals(List.of("kn-dhp-1", "kn-dhp-2"), texts.stream().map(SourceText::textId).toList());
        assertEquals("ඛුද්දක නිකාය", texts.get(0).nikaya());
        assertEquals("පළමු", texts.get(0).passages().get(0).text());
    }

    @Test
    void headingAtEndBooksTakeTheVersesBeforeTheHeading() throws Exception {
        // Like kn-thag.json: each elder's name (level 1) comes after his verses.
        String book = "{\"filename\":\"kn-thag\",\"pages\":[" + page(2,
                List.of(entry("heading", "ථෙරගාථාපාළි", 4), entry("heading", "1. එකකනිපාතො", 2),
                        entry("paragraph", "v1", null), entry("heading", "සුභූතිත්ථෙරගාථා", 1),
                        entry("paragraph", "v2", null), entry("heading", "මහාකොට්ඨිකත්ථෙරගාථා", 1)),
                List.of(entry("heading", "ථේරගාථා", 4), entry("heading", "1. ඒකකනිපාතය", 2),
                        entry("paragraph", "සුභූති ගාථාව", null), entry("heading", "සුභූති ස්ථවිරගාථා", 1),
                        entry("paragraph", "කොට්ඨික ගාථාව", null), entry("heading", "මහාකොට්ඨික ස්ථවිරගාථා", 1))) + "]}";
        Files.writeString(dir.resolve("kn-thag.json"), book);

        List<SourceText> texts = new TipitakaLkReader(List.of("kn-")).read(dir);

        assertEquals(2, texts.size());
        assertEquals("සුභූති ස්ථවිරගාථා", texts.get(0).title());
        assertEquals("සුභූති ගාථාව", texts.get(0).passages().get(0).text());
        assertEquals("කොට්ඨික ගාථාව", texts.get(1).passages().get(0).text());
    }

    @Test
    void includeFilterLimitsBooks() throws Exception {
        String book = "{\"filename\":\"mn-1\",\"pages\":[" + page(2,
                List.of(entry("heading", "x", 2), entry("paragraph", "p", null)),
                List.of(entry("heading", "මූලපරියාය සූත්‍රය", 2), entry("paragraph", "ස", null))) + "]}";
        Files.writeString(dir.resolve("mn-1.json"), book);

        assertEquals(0, new TipitakaLkReader(List.of("dn-")).read(dir).size());
        assertEquals("mn-1", new TipitakaLkReader(List.of("mn-")).read(dir).get(0).textId());
    }
}
