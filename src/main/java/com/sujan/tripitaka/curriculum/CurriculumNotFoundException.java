package com.sujan.tripitaka.curriculum;

/** No path or lesson with that id; answered with HTTP 404. */
public class CurriculumNotFoundException extends RuntimeException {

    public CurriculumNotFoundException(String kind, String id) {
        super("No " + kind + " with id " + id);
    }
}
