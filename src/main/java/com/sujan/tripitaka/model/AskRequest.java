package com.sujan.tripitaka.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param conversationId optional; omit to start a new conversation
 * @param language       optional; "en" (default, English suttas) or "si" (Sinhala Buddha Jayanthi Tripitaka)
 */
public record AskRequest(
        @NotBlank @Size(max = 1000) String question,
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String conversationId,
        @Pattern(regexp = "^(en|si)$") String language) {

    public AskRequest(String question, String conversationId) {
        this(question, conversationId, null);
    }

    public boolean sinhala() {
        return "si".equals(language);
    }
}
