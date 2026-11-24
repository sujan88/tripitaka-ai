package com.sujan.tripitaka.curriculum;

import com.sujan.tripitaka.curriculum.Curriculum.LearningPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurriculumRepositoryTest {

    @Test
    void loadsAllEightPathsInOrder() throws Exception {
        var curriculum = new CurriculumRepository();

        assertEquals(List.of("new-to-buddhism", "starting-meditation", "understanding-kamma", "rebirth-and-realms",
                        "not-self", "dependent-origination", "abhidhamma-mind", "mindfulness-to-awakening"),
                curriculum.paths().stream().map(LearningPath::id).toList());
        assertEquals(List.of(6, 6, 7, 6, 5, 8, 8, 7),
                curriculum.paths().stream().map(LearningPath::lessonCount).toList());
    }

    @Test
    void understandingKammaHasItsSevenLessons() throws Exception {
        var kamma = new CurriculumRepository().path("understanding-kamma").orElseThrow();

        assertTrue(kamma.available());
        assertEquals(List.of("AN 6.63", "AN 3.61", "MN 135", "AN 4.232", "MN 136", "AN 8.36", "MN 57"),
                kamma.lessons().stream().map(Curriculum.Lesson::keyText).toList());
        kamma.lessons().forEach(lesson -> {
            assertFalse(lesson.textIds().isEmpty(), lesson.id() + " has no text ids");
            assertTrue(lesson.quiz() != null && lesson.reflectPrompt() != null, lesson.id() + " is incomplete");
        });
        assertEquals("understanding-kamma", new CurriculumRepository().pathOfLesson("kamma-born-different")
                .orElseThrow().id());
    }

    @Test
    void loadsTheBigQuestionsAndDailySuttas() throws Exception {
        var curriculum = new CurriculumRepository();

        assertEquals(List.of("Kamma", "Death and rebirth", "The mind", "Practice"),
                curriculum.themes().stream().map(Curriculum.Theme::name).toList());
        curriculum.themes().forEach(theme -> assertEquals(3, theme.questions().size()));
        assertFalse(curriculum.dailySuttas().isEmpty());
    }

    private static CurriculumRepository load(Path dir, String pathYaml) throws Exception {
        Files.createDirectories(dir.resolve("paths"));
        Files.writeString(dir.resolve("paths/p.yaml"), pathYaml);
        Files.writeString(dir.resolve("themes.yaml"), "themes:\n  - id: t\n    name: T\n    questions: [Q?]\n");
        Files.writeString(dir.resolve("daily-suttas.yaml"), "suttas:\n  - id: d\n    title: D\n    text: x\n");
        return new CurriculumRepository(dir.toUri() + "/");
    }

    private static final String VALID = """
            id: p
            order: 1
            title: P
            level: beginner
            blurb: b
            lessons:
              - id: l1
                title: L
                keyText: MN 1
                question: Q?
                quiz: {question: Q?, options: [a, b], answer: 1}
            """;

    @Test
    void acceptsAMinimalValidPath(@TempDir Path dir) throws Exception {
        assertEquals("p", load(dir, VALID).paths().get(0).id());
    }

    @Test
    void rejectsAnUnknownLevel(@TempDir Path dir) {
        var failure = assertThrows(IllegalStateException.class,
                () -> load(dir, VALID.replace("level: beginner", "level: expert")));
        assertTrue(failure.getMessage().contains("level"));
    }

    @Test
    void rejectsAQuizAnswerOutsideItsOptions(@TempDir Path dir) {
        assertThrows(IllegalStateException.class, () -> load(dir, VALID.replace("answer: 1", "answer: 2")));
    }

    @Test
    void rejectsALinkToAPathThatDoesNotExist(@TempDir Path dir) {
        assertThrows(IllegalStateException.class, () -> load(dir, VALID.replace("blurb: b", "blurb: b\nafter: [nope]")));
    }

    @Test
    void rejectsAMisspelledField(@TempDir Path dir) {
        assertThrows(IllegalStateException.class, () -> load(dir, VALID.replace("blurb: b", "blurbb: b")));
    }

    @Test
    void rejectsAPathWithNeitherLessonsNorAPlannedCount(@TempDir Path dir) {
        String empty = VALID.substring(0, VALID.indexOf("lessons:")) + "lessons: []\n";
        assertThrows(IllegalStateException.class, () -> load(dir, empty));
    }
}
