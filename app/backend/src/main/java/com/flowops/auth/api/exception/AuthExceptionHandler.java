package com.flowops.auth.api.exception;

import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.application.shared.exception.AuthenticationRefusedException;
import com.flowops.auth.application.shared.exception.PasscodeAttemptLockedException;
import com.flowops.auth.application.shared.exception.PasscodeRejectedException;
import com.flowops.auth.application.shared.exception.RateLimitExceededException;
import com.flowops.auth.application.shared.exception.ReauthenticationRequiredException;
import com.flowops.auth.application.shared.exception.ResetTokenNotUsableException;
import com.flowops.auth.application.shared.exception.SignupClosedException;
import com.flowops.auth.application.terminatesession.RecordDeniedAttemptCommand;
import com.flowops.auth.application.terminatesession.RecordDeniedAttemptUseCase;
import com.flowops.auth.domain.enums.PasswordRule;
import com.flowops.auth.domain.exception.InvalidEmailAddressException;
import com.flowops.auth.domain.exception.PasswordPolicyViolationException;
import com.flowops.auth.domain.model.UserId;
import com.flowops.shared.web.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;

@RestControllerAdvice(basePackages = "com.flowops.auth")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {
    private final RecordDeniedAttemptUseCase recordDeniedAttemptUseCase;

    public AuthExceptionHandler(RecordDeniedAttemptUseCase recordDeniedAttemptUseCase) {
        this.recordDeniedAttemptUseCase = recordDeniedAttemptUseCase;
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> onAuthorizationDenied(
            AuthorizationDeniedException failure, HttpServletRequest request) {
        deniedAttemptFrom(request).ifPresent(recordDeniedAttemptUseCase::execute);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("PERMISSION_DENIED", "You do not have permission to do that."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> onMalformedPathValue(MethodArgumentTypeMismatchException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "REQUEST_INVALID",
                        "The request is not valid.",
                        List.of(new ErrorResponse.FieldViolation(failure.getName(), "MALFORMED"))));
    }

    private Optional<RecordDeniedAttemptCommand> deniedAttemptFrom(HttpServletRequest request) {
        ClientContext context = new ClientContext(request.getRemoteAddr(), request.getHeader("User-Agent"));
        Map<String, String> pathVariables = resolvedPathVariables(request);

        if (pathVariables.containsKey("reference")) {
            return asIdentifier(pathVariables.get("reference"))
                    .map(reference -> RecordDeniedAttemptCommand.terminationRefused(reference, context));
        }
        if (pathVariables.containsKey("userId")) {
            return asIdentifier(pathVariables.get("userId"))
                    .map(subject -> RecordDeniedAttemptCommand.viewRefused(UserId.of(subject), context));
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> resolvedPathVariables(HttpServletRequest request) {
        Object resolved = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return resolved instanceof Map ? (Map<String, String>) resolved : Map.of();
    }

    private Optional<UUID> asIdentifier(String segment) {
        try {
            return Optional.of(UUID.fromString(segment));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    @ExceptionHandler(AuthenticationRefusedException.class)
    public ResponseEntity<ErrorResponse> onAuthenticationRefused(AuthenticationRefusedException failure) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("AUTHENTICATION_REFUSED", "The email address or password is incorrect."));
    }

    @ExceptionHandler(SignupClosedException.class)
    public ResponseEntity<ErrorResponse> signupClosed(SignupClosedException refused) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("SIGNUP_CLOSED", refused.getMessage(), List.of()));
    }

    @ExceptionHandler(PasscodeRejectedException.class)
    public ResponseEntity<ErrorResponse> onPasscodeRejected(PasscodeRejectedException failure) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("PASSCODE_REJECTED", "The code is not valid. Request a new one if it expired."));
    }

    @ExceptionHandler(PasscodeAttemptLockedException.class)
    public ResponseEntity<ErrorResponse> onAttemptLocked(PasscodeAttemptLockedException failure) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(ErrorResponse.of(
                        "SIGNUP_ATTEMPT_LOCKED", "Too many wrong codes. Request a new code to continue."));
    }

    @ExceptionHandler(PasswordPolicyViolationException.class)
    public ResponseEntity<ErrorResponse> onPasswordPolicyViolation(PasswordPolicyViolationException failure) {
        List<ErrorResponse.FieldViolation> violations = failure.violations().stream()
                .map(PasswordRule::name)
                .map(rule -> new ErrorResponse.FieldViolation("password", rule))
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "PASSWORD_POLICY_VIOLATION", "The password does not meet the policy.", violations));
    }

    @ExceptionHandler(InvalidEmailAddressException.class)
    public ResponseEntity<ErrorResponse> onInvalidEmailAddress(InvalidEmailAddressException failure) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "EMAIL_INVALID",
                        "That is not a valid email address.",
                        List.of(new ErrorResponse.FieldViolation("email", "EMAIL_MALFORMED"))));
    }

    @ExceptionHandler(ReauthenticationRequiredException.class)
    public ResponseEntity<ErrorResponse> onReauthenticationRequired(ReauthenticationRequiredException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        "REAUTHENTICATION_REQUIRED", "Confirm your password before making this change."));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> onRateLimited(RateLimitExceededException failure) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of("RATE_LIMITED", "Too many attempts. Try again later."));
    }

    @ExceptionHandler(ResetTokenNotUsableException.class)
    public ResponseEntity<ErrorResponse> onResetTokenNotUsable(ResetTokenNotUsableException failure) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ErrorResponse.of("RESET_TOKEN_UNUSABLE", "This reset link cannot be used. Request another."));
    }
}
