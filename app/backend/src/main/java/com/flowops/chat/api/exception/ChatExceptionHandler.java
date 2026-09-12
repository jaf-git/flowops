package com.flowops.chat.api.exception;

import com.flowops.chat.application.buildprocess.TooFewMessagesException;
import com.flowops.chat.application.buildprocess.TooManyMessagesException;
import com.flowops.chat.application.managerooms.ThatRoomIsNotYoursToJoinException;
import com.flowops.chat.application.managerooms.UnknownRoomException;
import com.flowops.chat.application.shared.exception.ConversationNotDirectException;
import com.flowops.chat.application.shared.exception.ConversationNotFoundException;
import com.flowops.chat.application.shared.exception.CounterpartNotActiveException;
import com.flowops.chat.application.shared.exception.NotAuthenticatedException;
import com.flowops.chat.application.shared.exception.PersonNotFoundException;
import com.flowops.chat.domain.exception.MessageAlreadyConvertedException;
import com.flowops.chat.domain.exception.MessageDeletedException;
import com.flowops.chat.domain.exception.MessageEmptyException;
import com.flowops.chat.domain.exception.MessageTooLongException;
import com.flowops.chat.domain.exception.NotTheAuthorException;
import com.flowops.chat.domain.exception.NothingChangedException;
import com.flowops.shared.domain.RefusedByDomain;
import com.flowops.shared.published.PublishedRefusal;
import com.flowops.shared.web.ErrorResponse;
import com.flowops.task.application.shared.exception.AssigneeNotActiveException;
import com.flowops.task.application.shared.exception.AssigneeOutOfScopeException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.flowops.chat.api")
public class ChatExceptionHandler {
    @ExceptionHandler(ConversationNotFoundException.class)
    ResponseEntity<ErrorResponse> onConversationNotFound(ConversationNotFoundException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("CONVERSATION_NOT_FOUND", "There is no such conversation."));
    }

    @ExceptionHandler(ConversationNotDirectException.class)
    ResponseEntity<ErrorResponse> onConversationNotDirect(ConversationNotDirectException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONVERSATION_NOT_DIRECT", failure.getMessage()));
    }

    @ExceptionHandler(PersonNotFoundException.class)
    ResponseEntity<ErrorResponse> onPersonNotFound(PersonNotFoundException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("PERSON_NOT_FOUND", "There is nobody here with that identifier."));
    }

    @ExceptionHandler(CounterpartNotActiveException.class)
    ResponseEntity<ErrorResponse> onCounterpartNotActive(CounterpartNotActiveException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("COUNTERPART_NOT_ACTIVE", failure.getMessage()));
    }

    @ExceptionHandler(MessageEmptyException.class)
    ResponseEntity<ErrorResponse> onEmpty(MessageEmptyException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("MESSAGE_EMPTY", failure.getMessage()));
    }

    @ExceptionHandler(MessageTooLongException.class)
    ResponseEntity<ErrorResponse> onTooLong(MessageTooLongException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "MESSAGE_TOO_LONG",
                        failure.getMessage(),
                        List.of(new ErrorResponse.FieldViolation("body", String.valueOf(failure.limit())))));
    }

    @ExceptionHandler(NotTheAuthorException.class)
    ResponseEntity<ErrorResponse> onNotTheAuthor(NotTheAuthorException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("NOT_AUTHOR", failure.getMessage()));
    }

    @ExceptionHandler(MessageDeletedException.class)
    ResponseEntity<ErrorResponse> onDeleted(MessageDeletedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("MESSAGE_DELETED", failure.getMessage()));
    }

    @ExceptionHandler(NothingChangedException.class)
    ResponseEntity<ErrorResponse> onNothingChanged(NothingChangedException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("NOTHING_CHANGED", failure.getMessage()));
    }

    @ExceptionHandler(MessageAlreadyConvertedException.class)
    ResponseEntity<ErrorResponse> onAlreadyConverted(MessageAlreadyConvertedException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("MESSAGE_ALREADY_CONVERTED", failure.getMessage()));
    }

    @ExceptionHandler(NotAuthenticatedException.class)
    ResponseEntity<ErrorResponse> onNotAuthenticated(NotAuthenticatedException failure) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("NOT_AUTHENTICATED", failure.getMessage()));
    }

    @ExceptionHandler(AssigneeNotActiveException.class)
    ResponseEntity<ErrorResponse> onAssigneeInactive(AssigneeNotActiveException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "ASSIGNEE_NOT_ACTIVE",
                        "That person no longer has access. Choose somebody else.",
                        List.of(new ErrorResponse.FieldViolation("assigneeId", "NOT_ACTIVE"))));
    }

    @ExceptionHandler(AssigneeOutOfScopeException.class)
    ResponseEntity<ErrorResponse> onAssigneeOutOfScope(AssigneeOutOfScopeException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        "ASSIGNEE_OUT_OF_SCOPE",
                        "You can only give work to people who report to you.",
                        List.of(new ErrorResponse.FieldViolation("assigneeId", "OUT_OF_SCOPE"))));
    }

    @ExceptionHandler(RefusedByDomain.class)
    ResponseEntity<ErrorResponse> onRefusedByAnotherFeature(RefusedByDomain refusal) {
        return switch (refusal.code()) {
            case "DEADLINE_IN_THE_PAST" -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ErrorResponse.of(
                            "DEADLINE_IN_THE_PAST",
                            "That deadline has already passed. Choose one in the future.",
                            List.of(new ErrorResponse.FieldViolation("deadline", "IN_THE_PAST"))));
            case "TASK_TITLE_REQUIRED" -> ResponseEntity.badRequest()
                    .body(ErrorResponse.of(
                            "REQUEST_INVALID",
                            "Give the task a title.",
                            List.of(new ErrorResponse.FieldViolation("title", "REQUIRED"))));
            default -> throw refusal;
        };
    }

    @ExceptionHandler({TooFewMessagesException.class, TooManyMessagesException.class})
    ResponseEntity<ErrorResponse> onSelectionOutOfBounds(RuntimeException refusal) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("REQUEST_INVALID", refusal.getMessage()));
    }

    @ExceptionHandler(PublishedRefusal.class)
    ResponseEntity<ErrorResponse> onNeighbourRefusal(PublishedRefusal refusal) {
        HttpStatus status =
                switch (refusal.kind()) {
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case NOT_PERMITTED -> HttpStatus.FORBIDDEN;
                    case CONFLICT -> HttpStatus.CONFLICT;
                };
        return ResponseEntity.status(status).body(ErrorResponse.of(refusal.code(), refusal.getMessage(), List.of()));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<ErrorResponse> onDeniedBelowTheController(AuthorizationDeniedException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("NOT_PERMITTED", "You do not have permission to do that."));
    }

    @ExceptionHandler(UnknownRoomException.class)
    public ResponseEntity<ErrorResponse> onUnknownRoom(UnknownRoomException refused) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("UNKNOWN_ROOM", "That conversation is no longer there.", List.of()));
    }

    @ExceptionHandler(ThatRoomIsNotYoursToJoinException.class)
    public ResponseEntity<ErrorResponse> onRoomNotJoinable(ThatRoomIsNotYoursToJoinException refused) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "ROOM_MEMBERSHIP_IS_NOT_A_CHOICE",
                        "CHANNEL".equals(refused.kind())
                                ? "You join a role channel by being given that role."
                                : "That conversation does not take members by choice.",
                        List.of()));
    }
}
