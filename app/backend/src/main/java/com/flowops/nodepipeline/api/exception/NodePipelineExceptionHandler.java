package com.flowops.nodepipeline.api.exception;

import com.flowops.nodepipeline.application.WindowRunsBackwardsException;
import com.flowops.shared.published.PublishedRefusal;
import com.flowops.shared.web.ErrorResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.flowops.nodepipeline.api")
public class NodePipelineExceptionHandler {
    @ExceptionHandler(WindowRunsBackwardsException.class)
    public ResponseEntity<ErrorResponse> onBackwardsWindow(WindowRunsBackwardsException refusal) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "WINDOW_RUNS_BACKWARDS",
                        "A window has to end after it begins.",
                        List.of(
                                new ErrorResponse.FieldViolation("from", "NOT_BEFORE_TO"),
                                new ErrorResponse.FieldViolation("to", "NOT_AFTER_FROM"))));
    }

    @ExceptionHandler(PublishedRefusal.class)
    public ResponseEntity<ErrorResponse> onNeighbourRefusal(PublishedRefusal refusal) {
        HttpStatus status =
                switch (refusal.kind()) {
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case NOT_PERMITTED -> HttpStatus.FORBIDDEN;
                    case CONFLICT -> HttpStatus.CONFLICT;
                };
        return ResponseEntity.status(status).body(ErrorResponse.of(refusal.code(), refusal.getMessage(), List.of()));
    }
}
