package com.flowops.canvas.api.exception;

import com.flowops.canvas.application.stream.StreamNotAvailableException;
import com.flowops.shared.web.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.flowops.canvas.api")
public class CanvasExceptionHandler {
    @ExceptionHandler(StreamNotAvailableException.class)
    ResponseEntity<ErrorResponse> onStreamNotAvailable(StreamNotAvailableException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of("STREAM_NOT_AVAILABLE", "There is no such canvas stream."));
    }
}
