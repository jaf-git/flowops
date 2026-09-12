package com.flowops.shared.web;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> onInvalidRequest(MethodArgumentNotValidException failure) {
        List<ErrorResponse.FieldViolation> violations = failure.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldViolation(error.getField(), error.getCode()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("REQUEST_INVALID", "The request is not valid.", violations));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> onUnreadableBody(HttpMessageNotReadableException failure) {
        LOG.warn("A request body could not be read: {}", failure.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("REQUEST_MALFORMED", "The request body could not be read."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> onMissingResource(NoResourceFoundException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", "There is nothing at that address."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> onWrongMethod(HttpRequestMethodNotSupportedException failure) {
        Set<HttpMethod> allowed = failure.getSupportedHttpMethods();
        String verbs = allowed == null
                ? ""
                : allowed.stream().map(HttpMethod::name).sorted().collect(Collectors.joining(", "));

        ResponseEntity.BodyBuilder answer = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (!verbs.isEmpty()) {
            answer = answer.header(HttpHeaders.ALLOW, verbs);
        }

        return answer.body(ErrorResponse.of(
                "METHOD_NOT_ALLOWED",
                verbs.isEmpty()
                        ? "That address does not answer " + failure.getMethod() + " requests."
                        : "That address does not answer " + failure.getMethod() + " requests. It answers " + verbs
                                + "."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> onMissingParameter(MissingServletRequestParameterException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "PARAMETER_REQUIRED",
                        "The request is missing something it needs.",
                        List.of(new ErrorResponse.FieldViolation(failure.getParameterName(), "REQUIRED"))));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> onUnusableParameter(MethodArgumentTypeMismatchException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "The request is not valid.",
                        List.of(new ErrorResponse.FieldViolation(failure.getName(), "MALFORMED"))));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> onDenied(AuthorizationDeniedException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("NOT_PERMITTED", "You do not have permission to do that."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> onUnexpectedFailure(Exception failure) {
        LOG.error("Unhandled failure while serving a request.", failure);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "The request could not be completed."));
    }
}
