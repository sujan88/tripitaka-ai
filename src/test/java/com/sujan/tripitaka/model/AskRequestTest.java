package com.sujan.tripitaka.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AskRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static AskRequest request(String scope, String depth) {
        return new AskRequest("q", null, "si", scope, depth, null);
    }

    @Test
    void onlyDeepAddsTheLayersWhenADepthIsGiven() {
        assertTrue(request(null, "deep").includeLayers());
        assertTrue(request("sutta", "deep").includeLayers());
        assertFalse(request(null, "simple").includeLayers());
        assertFalse(request("all", "standard").includeLayers());
        assertFalse(request(null, "practice").includeLayers());
    }

    @Test
    void withoutADepthTheScopeDecidesAsBefore() {
        assertTrue(request(null, null).includeLayers());
        assertTrue(request("all", null).includeLayers());
        assertFalse(request("sutta", null).includeLayers());
        assertEquals("standard", request(null, null).depthOrDefault());
    }

    @Test
    void validatesDepthAndLessonId() {
        assertTrue(VALIDATOR.validate(new AskRequest("q", null, "en", null, "deep", "kamma-born-different")).isEmpty());
        assertFalse(VALIDATOR.validate(new AskRequest("q", null, "en", null, "very-deep", null)).isEmpty());
        assertFalse(VALIDATOR.validate(new AskRequest("q", null, "en", null, null, "../etc/passwd")).isEmpty());
    }
}
