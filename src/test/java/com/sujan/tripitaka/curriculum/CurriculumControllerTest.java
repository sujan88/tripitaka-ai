package com.sujan.tripitaka.curriculum;

import com.sujan.tripitaka.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CurriculumControllerTest {

    MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-11-24T08:00:00Z"), ZoneOffset.UTC);
        mvc = MockMvcBuilders.standaloneSetup(new CurriculumController(new CurriculumRepository(), clock))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listsEveryPathAsCurated() throws Exception {
        mvc.perform(get("/api/paths"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[*].curated", everyItem(is(true))))
                .andExpect(jsonPath("$[2].id").value("understanding-kamma"))
                .andExpect(jsonPath("$[2].lessonCount").value(7))
                .andExpect(jsonPath("$[2].available").value(true))
                .andExpect(jsonPath("$[2].lessonIds", hasSize(7)))
                .andExpect(jsonPath("$[0].available").value(false))
                .andExpect(jsonPath("$[0].lessonCount").value(6));
    }

    @Test
    void filtersByLevelInAnyCase() throws Exception {
        mvc.perform(get("/api/paths").param("level", "Intermediate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains("understanding-kamma", "rebirth-and-realms", "not-self")));
        mvc.perform(get("/api/paths").param("level", "all"))
                .andExpect(jsonPath("$", hasSize(8)));
    }

    @Test
    void rejectsAnUnknownLevel() throws Exception {
        mvc.perform(get("/api/paths").param("level", "expert"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pathDetailResolvesLessonsAndLinkedPaths() throws Exception {
        mvc.perform(get("/api/paths/understanding-kamma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("intermediate"))
                .andExpect(jsonPath("$.lessons", hasSize(7)))
                .andExpect(jsonPath("$.lessons[2].keyText").value("MN 135"))
                .andExpect(jsonPath("$.outcomes", hasSize(4)))
                .andExpect(jsonPath("$.before[0].title").value("New to Buddhism"))
                .andExpect(jsonPath("$.after[*].id", contains("rebirth-and-realms", "dependent-origination")))
                .andExpect(jsonPath("$.curated").value(true))
                .andExpect(jsonPath("$.reviewedBy", nullValue()));
    }

    @Test
    void lessonDetailHasItsPlaceInThePath() throws Exception {
        mvc.perform(get("/api/lessons/kamma-born-different"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(3))
                .andExpect(jsonPath("$.of").value(7))
                .andExpect(jsonPath("$.path.id").value("understanding-kamma"))
                .andExpect(jsonPath("$.previous.id").value("kamma-not-fate"))
                .andExpect(jsonPath("$.next.id").value("kamma-four-kinds"))
                .andExpect(jsonPath("$.textIds", contains("mn135")))
                .andExpect(jsonPath("$.quiz.options", hasSize(3)))
                .andExpect(jsonPath("$.quiz.curated").value(true));
    }

    @Test
    void firstAndLastLessonsHaveNoPreviousOrNext() throws Exception {
        mvc.perform(get("/api/lessons/kamma-intention")).andExpect(jsonPath("$.previous", nullValue()));
        mvc.perform(get("/api/lessons/kamma-ending")).andExpect(jsonPath("$.next", nullValue()));
    }

    @Test
    void unknownIdsAreNotFound() throws Exception {
        mvc.perform(get("/api/paths/nope")).andExpect(status().isNotFound());
        mvc.perform(get("/api/lessons/nope")).andExpect(status().isNotFound());
    }

    @Test
    void servesThemesAndTodaysSutta() throws Exception {
        mvc.perform(get("/api/themes"))
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].questions", hasSize(3)));
        mvc.perform(get("/api/daily-sutta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dhammapada 1–2"))
                .andExpect(jsonPath("$.curated").value(true));
    }

    @Test
    void sinhalaVersionsWithEnglishFallback() throws Exception {
        mvc.perform(get("/api/lessons/kamma-born-different").param("lang", "si"))
                .andExpect(jsonPath("$.title").value("මිනිසුන් එකිනෙකාට වෙනස්ව උපදින්නේ ඇයි?"))
                .andExpect(jsonPath("$.textIds", contains("mn-3-4-5")))
                .andExpect(jsonPath("$.quiz.options", hasSize(3)))
                .andExpect(jsonPath("$.quiz.answer").value(1))
                .andExpect(jsonPath("$.path.title").value("කර්මය අවබෝධ කර ගැනීම"))
                .andExpect(jsonPath("$.next.title").value("කර්ම වර්ග සතර"));
        // A path with only a Sinhala title and blurb keeps its English description fields.
        mvc.perform(get("/api/paths/not-self").param("lang", "si"))
                .andExpect(jsonPath("$.title").value("අනාත්මය"))
                .andExpect(jsonPath("$.keyTexts", contains("SN 22.59", "MN 22")));
        mvc.perform(get("/api/themes").param("lang", "si"))
                .andExpect(jsonPath("$[0].name").value("කර්මය"))
                .andExpect(jsonPath("$[0].questions", hasSize(3)));
        mvc.perform(get("/api/daily-sutta").param("lang", "si"))
                .andExpect(jsonPath("$.title").value("ධම්මපදය 1–2"));
        mvc.perform(get("/api/paths").param("lang", "en"))
                .andExpect(jsonPath("$[2].title").value("Understanding kamma"));
    }

    @Test
    void rejectsAnUnknownLanguage() throws Exception {
        mvc.perform(get("/api/paths").param("lang", "fr")).andExpect(status().isBadRequest());
    }
}
