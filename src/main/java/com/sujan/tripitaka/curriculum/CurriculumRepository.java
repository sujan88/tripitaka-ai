package com.sujan.tripitaka.curriculum;

import com.sujan.tripitaka.curriculum.Curriculum.DailySutta;
import com.sujan.tripitaka.curriculum.Curriculum.DailySuttas;
import com.sujan.tripitaka.curriculum.Curriculum.LearningPath;
import com.sujan.tripitaka.curriculum.Curriculum.Lesson;
import com.sujan.tripitaka.curriculum.Curriculum.Theme;
import com.sujan.tripitaka.curriculum.Curriculum.Themes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the curriculum YAML once at startup and checks it, so a typo in a lesson file stops the app from starting
 * instead of showing a broken page: unique ids, known levels, links to paths that exist, quiz answers in range.
 */
@Component
public class CurriculumRepository {

    private static final YAMLMapper YAML = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final List<LearningPath> paths;
    private final Map<String, LearningPath> pathsById = new LinkedHashMap<>();
    private final Map<String, LearningPath> pathOfLesson = new HashMap<>();
    private final Map<String, Lesson> lessonsById = new HashMap<>();
    private final List<Theme> themes;
    private final List<DailySutta> dailySuttas;

    @Autowired
    public CurriculumRepository() throws IOException {
        this("classpath:curriculum/");
    }

    /** @param location folder holding {@code paths/*.yaml}, {@code themes.yaml} and {@code daily-suttas.yaml} */
    CurriculumRepository(String location) throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        var loaded = new ArrayList<LearningPath>();
        for (Resource resource : resolver.getResources(location + "paths/*.yaml")) {
            loaded.add(read(resource, LearningPath.class));
        }
        loaded.sort(Comparator.comparingInt(LearningPath::order).thenComparing(LearningPath::id));
        this.paths = List.copyOf(loaded);
        this.themes = read(resolver.getResource(location + "themes.yaml"), Themes.class).themes();
        this.dailySuttas = read(resolver.getResource(location + "daily-suttas.yaml"), DailySuttas.class).suttas();
        index();
    }

    public List<LearningPath> paths() {
        return paths;
    }

    public Optional<LearningPath> path(String id) {
        return Optional.ofNullable(pathsById.get(id));
    }

    public Optional<Lesson> lesson(String id) {
        return Optional.ofNullable(lessonsById.get(id));
    }

    public Optional<LearningPath> pathOfLesson(String lessonId) {
        return Optional.ofNullable(pathOfLesson.get(lessonId));
    }

    public List<Theme> themes() {
        return themes;
    }

    public List<DailySutta> dailySuttas() {
        return dailySuttas;
    }

    private static <T> T read(Resource resource, Class<T> type) throws IOException {
        if (!resource.exists()) {
            throw new IllegalStateException("Missing curriculum file " + resource.getDescription());
        }
        try (InputStream in = resource.getInputStream()) {
            return YAML.readValue(in, type);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Invalid curriculum file " + resource.getFilename() + ": "
                    + failure.getMessage(), failure);
        }
    }

    private void index() {
        for (LearningPath path : paths) {
            require(path.id() != null && !path.id().isBlank(), "a path has no id");
            require(pathsById.put(path.id(), path) == null, "duplicate path id " + path.id());
            require(path.title() != null && !path.title().isBlank(), "path " + path.id() + " has no title");
            require(Curriculum.LEVELS.contains(path.level()),
                    "path " + path.id() + " has level '" + path.level() + "'; use one of " + Curriculum.LEVELS);
            require(path.available() || (path.plannedLessons() != null && path.plannedLessons() > 0),
                    "path " + path.id() + " needs lessons or plannedLessons");
            for (Lesson lesson : path.lessons()) {
                require(lesson.id() != null && !lesson.id().isBlank(), "a lesson in " + path.id() + " has no id");
                require(lessonsById.put(lesson.id(), lesson) == null, "duplicate lesson id " + lesson.id());
                pathOfLesson.put(lesson.id(), path);
                require(lesson.title() != null && lesson.question() != null && lesson.keyText() != null,
                        "lesson " + lesson.id() + " needs title, keyText and question");
                var quiz = lesson.quiz();
                if (quiz != null) {
                    require(quiz.options().size() >= 2, "quiz of " + lesson.id() + " needs at least two options");
                    require(quiz.answer() >= 0 && quiz.answer() < quiz.options().size(),
                            "quiz answer of " + lesson.id() + " is not one of its options");
                }
            }
        }
        for (LearningPath path : paths) {
            for (String linked : concat(path.before(), path.after())) {
                require(pathsById.containsKey(linked), "path " + path.id() + " links to unknown path " + linked);
            }
        }
        var themeIds = new java.util.HashSet<String>();
        for (Theme theme : themes) {
            require(theme.id() != null && themeIds.add(theme.id()), "theme ids must be present and unique");
            require(!theme.questions().isEmpty(), "theme " + theme.id() + " has no questions");
        }
        require(!dailySuttas.isEmpty(), "daily-suttas.yaml lists no suttas");
    }

    private static List<String> concat(List<String> a, List<String> b) {
        var all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static void require(boolean condition, String problem) {
        if (!condition) {
            throw new IllegalStateException("Curriculum error: " + problem);
        }
    }
}
