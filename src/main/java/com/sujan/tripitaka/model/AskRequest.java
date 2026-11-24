package com.sujan.tripitaka.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param conversationId optional; omit to start a new conversation
 * @param language       optional; "en" (default, English suttas) or "si" (Sinhala Buddha Jayanthi Tripitaka)
 * @param scope          optional, Sinhala only; "all" (default: suttas plus Abhidhamma, commentaries and
 *                       Visuddhimagga) or "sutta" (the suttas alone)
 */
public record AskRequest(
        @NotBlank @Size(max = 1000) String question,
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String conversationId,
        @Pattern(regexp = "^(en|si)$") String language,
        @Pattern(regexp = "^(sutta|all)$") String scope) {

    public AskRequest(String question, String conversationId) {
        this(question, conversationId, null, null);
    }

    public AskRequest(String question, String conversationId, String language) {
        this(question, conversationId, language, null);
    }

    public boolean sinhala() {
        return "si".equals(language);
    }

    public boolean suttasOnly() {
        return "sutta".equals(scope);
    }
}
