package com.flowops.chatassist.api;

import com.flowops.chat.application.shared.exception.ConversationNotFoundException;
import com.flowops.chatassist.application.SuggestWorkFromConversationUseCase;
import com.flowops.chatassist.domain.ProposedWork;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequestMapping("/api/chat-assist")
@Tag(name = "Chat assist", description = "What work a conversation describes, proposed and never applied")
public class ChatAssistController {
    private final SuggestWorkFromConversationUseCase suggestions;

    public ChatAssistController(SuggestWorkFromConversationUseCase suggestions) {
        this.suggestions = suggestions;
    }

    public record WorkSuggestionResponse(
            boolean available, String shape, Sourced title, Sourced assigneeId, Sourced deadline, List<Step> steps) {

        public record Sourced(Object value, String source) {
            static Sourced of(ProposedWork.Sourced<?> sourced) {
                return sourced == null
                        ? null
                        : new Sourced(
                                sourced.value() instanceof Instant when ? when.toString() : sourced.value(),
                                sourced.source().name());
            }
        }

        public record Step(Sourced title, String quotedFrom, Sourced assigneeId, Sourced deadline) {}
    }

    @Operation(
            summary = "What work this conversation describes (CHAT-ASSIST-SUGGEST-WORK-01)",
            description = "Asks a model inside this installation what work the thread describes, and"
                    + " returns only the parts of its answer that trace back to something somebody"
                    + " actually typed. Nothing is created, nothing is stored, and nothing is applied:"
                    + " the draft opens in a composer the person edits and submits themselves."
                    + " An empty list is the ordinary answer — most conversations describe no work.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The draft, or an empty list. Both are answers"),
        @ApiResponse(
                responseCode = "404",
                description = "No such conversation, or the caller is not a participant — indistinguishably")
    })
    @PostMapping("/conversations/{conversationId}/work-suggestion")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    public WorkSuggestionResponse workIn(@PathVariable UUID conversationId) {
        return suggestions
                .forConversation(conversationId)
                .map(ChatAssistController::described)
                .orElseGet(
                        () -> new WorkSuggestionResponse(suggestions.isAvailable(), null, null, null, null, List.of()));
    }

    private static WorkSuggestionResponse described(ProposedWork draft) {
        List<WorkSuggestionResponse.Step> steps = draft.steps().stream()
                .map(step -> new WorkSuggestionResponse.Step(
                        WorkSuggestionResponse.Sourced.of(step.title()),
                        step.quotedFrom(),
                        WorkSuggestionResponse.Sourced.of(step.assigneeId()),
                        WorkSuggestionResponse.Sourced.of(step.deadline())))
                .toList();

        return new WorkSuggestionResponse(
                true,
                draft.shape().name(),
                WorkSuggestionResponse.Sourced.of(draft.title()),
                WorkSuggestionResponse.Sourced.of(draft.assigneeId()),
                WorkSuggestionResponse.Sourced.of(draft.deadline()),
                steps);
    }

    @RestControllerAdvice(basePackages = "com.flowops.chatassist.api")
    static class ChatAssistExceptionHandler {
        @ExceptionHandler(ConversationNotFoundException.class)
        ResponseEntity<ErrorResponse> onUnknownConversation(ConversationNotFoundException refusal) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of("CONVERSATION_NOT_FOUND", "There is no such conversation.", List.of()));
        }

        @ExceptionHandler(AuthorizationDeniedException.class)
        ResponseEntity<ErrorResponse> onDenied(AuthorizationDeniedException refusal) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorResponse.of("NOT_PERMITTED", "You do not have permission to do that.", List.of()));
        }
    }
}
