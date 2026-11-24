package com.sujan.tripitaka.curriculum;

import com.sujan.tripitaka.curriculum.Curriculum.DailySutta;
import com.sujan.tripitaka.curriculum.Curriculum.LearningPath;
import com.sujan.tripitaka.curriculum.Curriculum.Lesson;
import com.sujan.tripitaka.curriculum.Curriculum.Quiz;
import com.sujan.tripitaka.curriculum.Curriculum.Theme;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Read-only curriculum API for the home, path and lesson pages (and the mobile app). Every item it returns is
 * curated, so each carries {@code "curated": true} and the reviewer's name once a teacher has checked it.
 */
@RestController
@RequestMapping("/api")
public class CurriculumController {

    private final CurriculumRepository curriculum;
    private final Clock clock;

    @Autowired
    public CurriculumController(CurriculumRepository curriculum) {
        this(curriculum, Clock.systemDefaultZone());
    }

    CurriculumController(CurriculumRepository curriculum, Clock clock) {
        this.curriculum = curriculum;
        this.clock = clock;
    }

    public record PathRef(String id, String title) {
    }

    public record LessonRef(String id, String title, String keyText) {
    }

    /** @param available false while the path's lessons are still being written */
    public record PathSummary(String id, String title, String level, String blurb, List<String> keyTexts,
                              int lessonCount, List<String> lessonIds, boolean available,
                              boolean curated, String reviewedBy) {
    }

    public record PathDetail(String id, String title, String level, String blurb, String description,
                             List<String> keyTexts, int minutesPerLesson, int lessonCount, boolean available,
                             List<String> outcomes, List<PathRef> before, List<PathRef> after,
                             List<String> questions, List<LessonRef> lessons, boolean curated, String reviewedBy) {
    }

    public record QuizView(String question, List<String> options, int answer, String explanation,
                           boolean curated, String reviewedBy) {
    }

    /** @param number this lesson's place in its path, from 1 */
    public record LessonDetail(String id, String title, String keyText, List<String> textIds, String question,
                               String reflectPrompt, QuizView quiz, PathRef path, int number, int of,
                               LessonRef previous, LessonRef next, boolean curated, String reviewedBy) {
    }

    public record ThemeView(String id, String name, List<String> questions, boolean curated) {
    }

    public record DailySuttaView(String id, String title, String text, String note, String question,
                                 boolean curated, String reviewedBy) {
    }

    /** @param level optional: beginner, intermediate or advanced (any case); omit or "all" for every path */
    @GetMapping("/paths")
    public List<PathSummary> paths(@RequestParam(required = false) String level) {
        String wanted = level == null || level.isBlank() ? "all" : level.trim().toLowerCase(Locale.ROOT);
        if (!wanted.equals("all") && !Curriculum.LEVELS.contains(wanted)) {
            throw new CurriculumRequestException("level must be all, beginner, intermediate or advanced");
        }
        return curriculum.paths().stream()
                .filter(p -> wanted.equals("all") || p.level().equals(wanted))
                .map(CurriculumController::summary)
                .toList();
    }

    @GetMapping("/paths/{id}")
    public PathDetail path(@PathVariable String id) {
        LearningPath path = curriculum.path(id).orElseThrow(() -> new CurriculumNotFoundException("path", id));
        return new PathDetail(path.id(), path.title(), path.level(), path.blurb(), path.description(),
                path.keyTexts(), path.minutesPerLesson(), path.lessonCount(), path.available(), path.outcomes(),
                refs(path.before()), refs(path.after()), path.questions(),
                path.lessons().stream().map(CurriculumController::ref).toList(), true, path.reviewedBy());
    }

    @GetMapping("/lessons/{id}")
    public LessonDetail lesson(@PathVariable String id) {
        Lesson lesson = curriculum.lesson(id).orElseThrow(() -> new CurriculumNotFoundException("lesson", id));
        LearningPath path = curriculum.pathOfLesson(id).orElseThrow();
        List<Lesson> lessons = path.lessons();
        int index = lessons.indexOf(lesson);
        Quiz quiz = lesson.quiz();
        return new LessonDetail(lesson.id(), lesson.title(), lesson.keyText(), lesson.textIds(), lesson.question(),
                lesson.reflectPrompt(),
                quiz == null ? null : new QuizView(quiz.question(), quiz.options(), quiz.answer(), quiz.explanation(),
                        true, quiz.reviewedBy()),
                new PathRef(path.id(), path.title()), index + 1, lessons.size(),
                index > 0 ? ref(lessons.get(index - 1)) : null,
                index < lessons.size() - 1 ? ref(lessons.get(index + 1)) : null,
                true, lesson.reviewedBy());
    }

    /** The Big questions, grouped by theme: the questions are curated, their answers are written by AI. */
    @GetMapping("/themes")
    public List<ThemeView> themes() {
        return curriculum.themes().stream()
                .map((Theme t) -> new ThemeView(t.id(), t.name(), t.questions(), true))
                .toList();
    }

    /** Today's sutta: the daily list in turn, one entry per day. */
    @GetMapping("/daily-sutta")
    public DailySuttaView dailySutta() {
        List<DailySutta> suttas = curriculum.dailySuttas();
        DailySutta today = suttas.get((int) (LocalDate.now(clock).toEpochDay() % suttas.size()));
        return new DailySuttaView(today.id(), today.title(), today.text(), today.note(), today.question(),
                true, today.reviewedBy());
    }

    private static PathSummary summary(LearningPath path) {
        return new PathSummary(path.id(), path.title(), path.level(), path.blurb(), path.keyTexts(),
                path.lessonCount(), path.lessons().stream().map(Lesson::id).toList(), path.available(),
                true, path.reviewedBy());
    }

    private List<PathRef> refs(List<String> ids) {
        return ids.stream().map(id -> curriculum.path(id).orElseThrow())
                .map(p -> new PathRef(p.id(), p.title()))
                .toList();
    }

    private static LessonRef ref(Lesson lesson) {
        return new LessonRef(lesson.id(), lesson.title(), lesson.keyText());
    }
}
