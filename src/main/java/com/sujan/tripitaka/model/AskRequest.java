package com.sujan.tripitaka.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param conversationId optional; omit to start a new conversation
 * @param language       optional; "en" (default, English suttas) or "si" (Sinhala Buddha Jayanthi Tripitaka)
 * @param scope          optional, Sinhala only; "all" (default: suttas plus Abhidhamma, commentaries and
 *                       Visuddhimagga) or "sutta" (the suttas alone)
 * @param depth          optional; "simple", "standard" (default), "deep" or "practice". When given, it also sets
 *                       the Sinhala scope: only "deep" adds the Abhidhamma, commentaries and Visuddhimagga
 * @param lessonId       optional; the curated lesson this question belongs to: its key text is preferred in
 *                       retrieval and the answer is centred on it
 */
public record AskRequest(
        @NotBlank @Size(max = 1000) String question,
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String conversationId,
        @Pattern(regexp = "^(en|si)$") String language,
        @Pattern(regexp = "^(sutta|all)$") String scope,
        @Pattern(regexp = "^(simple|standard|deep|practice)$") String depth,
        @Pattern(regexp = "^[a-z0-9-]{1,80}$") String lessonId) {

    public AskRequest(String question, String conversationId) {
        this(question, conversationId, null, null);
    }

    public AskRequest(String question, String conversationId, String language) {
        this(question, conversationId, language, null);
    }

    public AskRequest(String question, String conversationId, String language, String scope) {
        this(question, conversationId, language, scope, null, null);
    }

    /** "standard" unless the request asked for another depth. */
    public String depthOrDefault() {
        return depth == null ? "standard" : depth;
    }

    /**
     * Whether a Sinhala search adds the explanatory layers: "deep" does, other explicit depths keep to the suttas,
     * and without a depth the scope decides (all layers unless "sutta").
     */
    public boolean includeLayers() {
        return depth != null ? depth.equals("deep") : !suttasOnly();
    }

    public boolean sinhala() {
        return "si".equals(language);
    }

    public boolean suttasOnly() {
        return "sutta".equals(scope);
    }
}
