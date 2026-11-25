package com.sujan.tripitaka.error;

import com.sujan.tripitaka.cost.UsageGuard;
import com.sujan.tripitaka.curriculum.CurriculumNotFoundException;
import com.sujan.tripitaka.curriculum.CurriculumRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
                        + "scope must be sutta or all; depth must be simple, standard, deep or practice; "
                        + "lessonId must be a lesson id.");
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

    /** Over a usage limit: 429 with Retry-After, in English and Sinhala (the page shows this text as it is). */
    @ExceptionHandler(UsageGuard.LimitReached.class)
    public ResponseEntity<ProblemDetail> limited(UsageGuard.LimitReached exception) {
        long minutes = Math.max(1, (exception.retryAfterSeconds() + 59) / 60);
        String wait = minutes >= 60 ? "tomorrow" : "in " + minutes + " minutes";
        String waitSi = minutes >= 60 ? "හෙට" : "මිනිත්තු " + minutes + " කින්";
        String detail = exception.everyone()
                ? "Today's question limit for the app has been reached. Please try again " + wait + ". "
                        + "අද දිනයේ ප්‍රශ්න සීමාව ඉක්මවා ඇත. කරුණාකර " + waitSi + " නැවත උත්සාහ කරන්න."
                : "You've asked many questions in a short time. Please try again " + wait + ". "
                        + "ඔබ කෙටි කාලයක් තුළ බොහෝ ප්‍රශ්න ඇසුවා. කරුණාකර " + waitSi + " නැවත උත්සාහ කරන්න.";
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfterSeconds()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, detail));
    }

    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail failure(RuntimeException exception) {
        // Log the cause server-side; never echo model or index internals to the client.
        log.error("Ask failed errorType={}", exception.getClass().getSimpleName(), exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Could not answer right now. Check that Qdrant is running and the corpus is ingested.");
    }
}
