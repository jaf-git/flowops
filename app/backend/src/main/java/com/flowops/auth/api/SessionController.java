package com.flowops.auth.api;

import com.flowops.auth.api.dto.SessionSummaryResponse;
import com.flowops.auth.api.mapper.AuthDtoMapper;
import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.application.terminatesession.TerminateSessionCommand;
import com.flowops.auth.application.terminatesession.TerminateSessionUseCase;
import com.flowops.auth.application.viewownsessions.ViewOwnSessionsUseCase;
import com.flowops.auth.application.viewusersessions.ViewUserSessionsQuery;
import com.flowops.auth.application.viewusersessions.ViewUserSessionsUseCase;
import com.flowops.auth.domain.model.UserId;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Sessions", description = "Seeing where an account is signed in, and cutting a session off.")
public class SessionController {
    private final ViewOwnSessionsUseCase viewOwnSessionsUseCase;
    private final ViewUserSessionsUseCase viewUserSessionsUseCase;
    private final TerminateSessionUseCase terminateSessionUseCase;
    private final AuthDtoMapper mapper;

    public SessionController(
            ViewOwnSessionsUseCase viewOwnSessionsUseCase,
            ViewUserSessionsUseCase viewUserSessionsUseCase,
            TerminateSessionUseCase terminateSessionUseCase,
            AuthDtoMapper mapper) {
        this.viewOwnSessionsUseCase = viewOwnSessionsUseCase;
        this.viewUserSessionsUseCase = viewUserSessionsUseCase;
        this.terminateSessionUseCase = terminateSessionUseCase;
        this.mapper = mapper;
    }

    @Operation(
            summary = "List the caller's own active sessions",
            description =
                    """
                    Use case AUTH-VIEW-SESSIONS-01. Permission required: SESSION_VIEW_OWN, satisfied by
                    construction in the use case rather than checked by an endpoint annotation
                    (DECISION-PERMISSION-ENFORCEMENT-POINT-01; AUTH_02_ACCESS_CONTROL's enforcement-point
                    table states this for every permission).

                    There is no way to ask for anyone else's list: the subject is read from the session, and
                    the endpoint takes no parameter that could name a different person.

                    Each entry carries an opaque reference, never the session identifier — the identifier is
                    a bearer credential and this response is read on screen.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The caller's active sessions, most recently used first."),
        @ApiResponse(responseCode = "401", description = "There is no live session.")
    })
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionSummaryResponse>> ownSessions() {
        return ResponseEntity.ok(mapper.toResponses(viewOwnSessionsUseCase.execute()));
    }

    @Operation(
            summary = "List a person's active sessions",
            description =
                    """
                    Use case AUTH-TERMINATE-SESSION-01. Permission required: SESSION_VIEW_ANY, checked on this
                    endpoint before the use case runs (DECISION-PERMISSION-ENFORCEMENT-POINT-01). Owner-only
                    and not delegable.

                    A caller without the permission is refused and the attempt is recorded with actor and
                    target. A **successful** read is recorded too (AUTH_NOTE_10): this returns another
                    person's addresses, devices and coarse locations, and an access to the most sensitive
                    data the product holds does not go unrecorded. Listing one's own sessions records
                    nothing.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "That person's active sessions."),
        @ApiResponse(
                responseCode = "400",
                description = "The identifier in the path is not a well-formed value.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "There is no live session. Refused by the security chain."),
        @ApiResponse(
                responseCode = "403",
                description = "The caller does not hold SESSION_VIEW_ANY.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasAuthority('SESSION_VIEW_ANY')")
    @GetMapping("/users/{userId}/sessions")
    public ResponseEntity<List<SessionSummaryResponse>> sessionsOf(
            @PathVariable UUID userId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(mapper.toResponses(
                viewUserSessionsUseCase.execute(new ViewUserSessionsQuery(UserId.of(userId), context(httpRequest)))));
    }

    @Operation(
            summary = "Terminate a session",
            description =
                    """
                    Use case AUTH-TERMINATE-SESSION-01. Permission required: SESSION_TERMINATE_ANY, checked on
                    this endpoint before the use case runs. Owner-only and not delegable — it is the strictest
                    control in AUTH, because it can remove anyone's access.

                    A sensitive action: the caller's session must be inside its re-authentication window
                    (AUTH-REAUTH-01) or they are challenged first.

                    Idempotent. A reference naming a session that has already ended succeeds and changes
                    nothing, and so does one that never named a session at all — the two are indistinguishable
                    here, which is deliberate.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "That session is no longer usable."),
        @ApiResponse(
                responseCode = "400",
                description = "The reference in the path is not a well-formed value.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "There is no live session. Refused by the security chain."),
        @ApiResponse(
                responseCode = "403",
                description = "The caller does not hold SESSION_TERMINATE_ANY, or has not re-authenticated.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasAuthority('SESSION_TERMINATE_ANY')")
    @DeleteMapping("/sessions/{reference}")
    public ResponseEntity<Void> terminate(@PathVariable UUID reference, HttpServletRequest httpRequest) {
        terminateSessionUseCase.execute(new TerminateSessionCommand(reference, context(httpRequest)));
        return ResponseEntity.noContent().build();
    }

    private ClientContext context(HttpServletRequest request) {
        return new ClientContext(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
