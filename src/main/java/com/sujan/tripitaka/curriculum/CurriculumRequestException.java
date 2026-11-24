package com.sujan.tripitaka.curriculum;

/** A curriculum request with an invalid parameter (e.g. an unknown level); answered with HTTP 400. */
public class CurriculumRequestException extends RuntimeException {

    public CurriculumRequestException(String message) {
        super(message);
    }
}
