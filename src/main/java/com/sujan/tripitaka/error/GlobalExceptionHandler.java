package com.sujan.tripitaka.error;

import com.sujan.tripitaka.curriculum.CurriculumNotFoundException;
import com.sujan.tripitaka.curriculum.CurriculumRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalid(MethodArgumentNotValidException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Question must be 1-1000 characters; conversationId must be a UUID; language must be en or si; "
                        + "scope must be sutta or all.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body must be valid JSON.");
    }

    @ExceptionHandler(CurriculumNotFoundException.class)
    public ProblemDetail notFound(CurriculumNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(CurriculumRequestException.class)
    public ProblemDetail badCurriculumRequest(CurriculumRequestException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail failure(RuntimeException exception) {
        // Log the cause server-side; never echo model or index internals to the client.
        log.error("Ask failed errorType={}", exception.getClass().getSimpleName(), exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Could not answer right now. Check that Qdrant is running and the corpus is ingested.");
    }
}
