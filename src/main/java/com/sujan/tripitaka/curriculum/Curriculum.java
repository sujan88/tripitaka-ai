package com.sujan.tripitaka.curriculum;

import java.util.List;
import java.util.Set;

/**
 * The curriculum as written in {@code src/main/resources/curriculum}: paths of lessons, the Big questions and the
 * daily suttas. Everything here is curated by people (never generated); {@code reviewedBy} names the teacher who
 * checked an item, and stays null until someone has.
 */
public final class Curriculum {

    public static final Set<String> LEVELS = Set.of("beginner", "intermediate", "advanced");

    private Curriculum() {
    }

    /**
     * @param plannedLessons for a path whose lessons are not written yet: how many it will have
     * @param before         ids of paths to take first
     * @param after          ids of paths to take next
     * @param questions      "Questions people ask here"
     */
    public record LearningPath(String id, int order, String title, String level, String blurb, String description,
                               List<String> keyTexts, Integer minutesPerLesson, Integer plannedLessons,
                               String reviewedBy, List<String> outcomes, List<String> before, List<String> after,
                               List<String> questions, List<Lesson> lessons) {

        public LearningPath {
            minutesPerLesson = minutesPerLesson == null ? 15 : minutesPerLesson;
            keyTexts = nullSafe(keyTexts);
            outcomes = nullSafe(outcomes);
            before = nullSafe(before);
            after = nullSafe(after);
            questions = nullSafe(questions);
            lessons = nullSafe(lessons);
        }

        /** A path can be started once its lessons are written. */
        public boolean available() {
            return !lessons.isEmpty();
        }

        public int lessonCount() {
            return available() ? lessons.size() : plannedLessons == null ? 0 : plannedLessons;
        }
    }

    /**
     * @param keyText  the reference shown to people, e.g. "MN 135"
     * @param textIds  corpus ids of the key texts (e.g. "mn135"), preferred in retrieval for this lesson
     * @param question what the lesson asks the AI to explain
     */
    public record Lesson(String id, String title, String keyText, List<String> textIds, String question,
                         String reflectPrompt, Quiz quiz, String reviewedBy) {

        public Lesson {
            textIds = nullSafe(textIds);
        }
    }

    /** @param answer index of the right option */
    public record Quiz(String question, List<String> options, int answer, String explanation, String reviewedBy) {

        public Quiz {
            options = nullSafe(options);
        }
    }

    public record Theme(String id, String name, List<String> questions) {

        public Theme {
            questions = nullSafe(questions);
        }
    }

    public record Themes(List<Theme> themes) {

        public Themes {
            themes = nullSafe(themes);
        }
    }

    public record DailySutta(String id, String title, String text, String note, String question, String reviewedBy) {
    }

    public record DailySuttas(List<DailySutta> suttas) {

        public DailySuttas {
            suttas = nullSafe(suttas);
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}
