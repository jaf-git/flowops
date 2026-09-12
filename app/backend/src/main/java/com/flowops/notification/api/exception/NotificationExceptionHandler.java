package com.flowops.notification.api.exception;

import com.flowops.notification.application.shared.exception.NotADisableableGroupException;
import com.flowops.notification.application.shared.exception.NotAuthenticatedException;
import com.flowops.notification.application.shared.exception.NotYoursException;
import com.flowops.shared.web.ErrorResponse;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.flowops.notification")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NotificationExceptionHandler {
    @ExceptionHandler(NotAuthenticatedException.class)
    public ResponseEntity<ErrorResponse> onNoSession(NotAuthenticatedException failure) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("NOT_AUTHENTICATED", "Sign in to continue."));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> onDenied(AuthorizationDeniedException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("NOT_PERMITTED", "You do not have permission to do that."));
    }

    @ExceptionHandler(NotYoursException.class)
    public ResponseEntity<ErrorResponse> onNotYours(NotYoursException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", "That notification is not available."));
    }

    @ExceptionHandler(NotADisableableGroupException.class)
    public ResponseEntity<ErrorResponse> onNotDisableable(NotADisableableGroupException failure) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of(
                        "NOT_DISABLEABLE",
                        "Escalations always arrive.",
                        List.of(new ErrorResponse.FieldViolation(failure.group().toLowerCase(), "NO_SWITCH"))));
    }
}
