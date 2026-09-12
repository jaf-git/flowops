package com.flowops.auth.api;

import com.flowops.auth.api.dto.ChangePasswordRequest;
import com.flowops.auth.api.dto.CompletePasswordResetRequest;
import com.flowops.auth.api.dto.CompleteSignupRequest;
import com.flowops.auth.api.dto.LoginRequest;
import com.flowops.auth.api.dto.ReauthenticateRequest;
import com.flowops.auth.api.dto.ReauthenticateResponse;
import com.flowops.auth.api.dto.RequestPasscodeRequest;
import com.flowops.auth.api.dto.RequestPasswordResetRequest;
import com.flowops.auth.api.dto.SessionContextResponse;
import com.flowops.auth.api.mapper.AuthDtoMapper;
import com.flowops.auth.application.changepassword.ChangePasswordCommand;
import com.flowops.auth.application.changepassword.ChangePasswordUseCase;
import com.flowops.auth.application.completepasswordreset.CompletePasswordResetCommand;
import com.flowops.auth.application.completepasswordreset.CompletePasswordResetUseCase;
import com.flowops.auth.application.completesignup.CompleteSignupCommand;
import com.flowops.auth.application.completesignup.CompleteSignupUseCase;
import com.flowops.auth.application.login.LoginCommand;
import com.flowops.auth.application.login.LoginUseCase;
import com.flowops.auth.application.logout.LogoutCommand;
import com.flowops.auth.application.logout.LogoutUseCase;
import com.flowops.auth.application.reauthenticate.ReauthenticateCommand;
import com.flowops.auth.application.reauthenticate.ReauthenticateUseCase;
import com.flowops.auth.application.requestpasswordreset.RequestPasswordResetCommand;
import com.flowops.auth.application.requestpasswordreset.RequestPasswordResetUseCase;
import com.flowops.auth.application.requestsignuppasscode.RequestSignupPasscodeCommand;
import com.flowops.auth.application.requestsignuppasscode.RequestSignupPasscodeUseCase;
import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.application.validateresettoken.ValidateResetTokenUseCase;
import com.flowops.auth.application.viewsessioncontext.ViewSessionContextUseCase;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Identity and access", description = "Signup, login, logout, and the caller's own session context.")
public class AuthController {
    private final RequestSignupPasscodeUseCase requestSignupPasscodeUseCase;
    private final CompleteSignupUseCase completeSignupUseCase;
    private final LoginUseCase loginUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ReauthenticateUseCase reauthenticateUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final ViewSessionContextUseCase viewSessionContextUseCase;
    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ValidateResetTokenUseCase validateResetTokenUseCase;
    private final CompletePasswordResetUseCase completePasswordResetUseCase;
    private final AuthDtoMapper mapper;

    public AuthController(
            RequestSignupPasscodeUseCase requestSignupPasscodeUseCase,
            CompleteSignupUseCase completeSignupUseCase,
            LoginUseCase loginUseCase,
            LogoutUseCase logoutUseCase,
            ReauthenticateUseCase reauthenticateUseCase,
            ChangePasswordUseCase changePasswordUseCase,
            ViewSessionContextUseCase viewSessionContextUseCase,
            RequestPasswordResetUseCase requestPasswordResetUseCase,
            ValidateResetTokenUseCase validateResetTokenUseCase,
            CompletePasswordResetUseCase completePasswordResetUseCase,
            AuthDtoMapper mapper) {
        this.requestSignupPasscodeUseCase = requestSignupPasscodeUseCase;
        this.completeSignupUseCase = completeSignupUseCase;
        this.loginUseCase = loginUseCase;
        this.logoutUseCase = logoutUseCase;
        this.reauthenticateUseCase = reauthenticateUseCase;
        this.changePasswordUseCase = changePasswordUseCase;
        this.viewSessionContextUseCase = viewSessionContextUseCase;
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
        this.validateResetTokenUseCase = validateResetTokenUseCase;
        this.completePasswordResetUseCase = completePasswordResetUseCase;
        this.mapper = mapper;
    }

    @Operation(
            summary = "Request a signup passcode",
            description =
                    """
                    Use case AUTH-REGISTER-OWNER-01. Permission required: none — this runs before any principal exists.

                    The response is identical whether or not the address is already registered
                    (DECISION-SIGNUP-ENUMERATION-01). A free address is sent a single-use passcode; a registered
                    one is sent a notice telling its owner someone tried to sign up. Nothing in the response
                    distinguishes the two.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Accepted. Reveals nothing about whether the address exists."),
        @ApiResponse(
                responseCode = "400",
                description = "The address is missing or malformed.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "429",
                description = "Too many attempts within the window.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/signup/passcode")
    public ResponseEntity<Void> requestSignupPasscode(
            @Valid @RequestBody RequestPasscodeRequest request, HttpServletRequest httpRequest) {
        requestSignupPasscodeUseCase.execute(new RequestSignupPasscodeCommand(request.email(), context(httpRequest)));
        return ResponseEntity.accepted().build();
    }

    @Operation(
            summary = "Ask for a password reset link",
            description =
                    """
                    Use case AUTH-RESET-PASSWORD-01. Permission required: none, and deliberately so — a person who
                    could authenticate would not need this. The absence of `@PreAuthorize` here is a decision, not
                    an omission: the address is the whole of the request and there is no principal to require
                    anything of.

                    **The response is `202` and empty whatever happens.** An active account, an address with no
                    account, a deactivated account, an unclaimed installation, and a caller who has exceeded the
                    rate limit all receive the same answer — including the rate limit, which is the only limited
                    action in this API that does not answer `429`. A distinguishable refusal here would hand back
                    the channel the uniform response exists to close, and a cheaper one than the original.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "202",
                description = "Accepted. Reveals nothing about whether the address has an account."),
        @ApiResponse(
                responseCode = "400",
                description = "The address is missing or malformed. This is the only thing the response can say.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(
            @Valid @RequestBody RequestPasswordResetRequest request, HttpServletRequest httpRequest) {
        requestPasswordResetUseCase.execute(new RequestPasswordResetCommand(request.email(), context(httpRequest)));
        return ResponseEntity.accepted().build();
    }

    @Operation(
            summary = "Check whether a reset link is still good",
            description =
                    """
                    Use case AUTH-RESET-PASSWORD-01. Permission required: none — the token in the link is the whole
                    authorisation, and the caller has no session by definition.

                    **The success answer carries no body at all.** Not the address, not a name, not an identifier:
                    anything returned here would describe an account to whoever holds the token, and unlike an
                    invitation — which shows somebody what they are being offered — this opens something that
                    already exists and its owner already knows what it is.

                    Unknown, expired and already-spent tokens are refused identically, with one code and no detail.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "The link may still be used. Nothing else is disclosed."),
        @ApiResponse(
                responseCode = "410",
                description = "Unknown, expired or already used — refused identically in all three cases.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/password-reset/{token}")
    public ResponseEntity<Void> validateResetToken(@PathVariable String token) {
        validateResetTokenUseCase.execute(token);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Set a new password using a reset link",
            description =
                    """
                    Use case AUTH-RESET-PASSWORD-01. Permission required: none, deliberately — see above.

                    In one transaction the new hash is stored, the token is spent, **every session the account
                    holds ends**, and the event is appended. No session is opened: the person signs in afterwards
                    with what they just chose, which is why this returns nothing about where to land.

                    A password refused for policy — including for being the one already in use — does **not** spend
                    the token, so the same link still works.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "The password is set and every session has ended."),
        @ApiResponse(
                responseCode = "400",
                description = "The password fails policy; the unmet rule is named and the link still works.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "410",
                description = "Unknown, expired or already used — refused identically in all three cases.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/password-reset/complete")
    public ResponseEntity<Void> completePasswordReset(
            @Valid @RequestBody CompletePasswordResetRequest request, HttpServletRequest httpRequest) {
        completePasswordResetUseCase.execute(
                new CompletePasswordResetCommand(request.token(), request.newPassword(), context(httpRequest)));
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Complete signup and become the owner",
            description =
                    """
                    Use case AUTH-REGISTER-OWNER-01. Permission required: none — this is where the first identity is created.

                    Creates the owner, stores the credential as a hash, opens the session, and appends the
                    completion event, all in one transaction.

                    It creates no workspace and calls no other feature. Naming the workspace is a separate goal
                    reached at WORKSPACE-SETUP-01 (DECISION-OWNER-REGISTRATION-SPLIT-01), so this ends with the
                    person authenticated, the installation claimed, and setup not yet done.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The account exists and the session is open."),
        @ApiResponse(
                responseCode = "400",
                description = "The password failed the policy; the unmet rule is named in the details.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "The passcode is wrong, expired, spent, or was never issued.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "423",
                description = "The attempt is locked; a fresh passcode must be requested.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/signup")
    public ResponseEntity<SessionContextResponse> completeSignup(
            @Valid @RequestBody CompleteSignupRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toResponse(completeSignupUseCase.execute(new CompleteSignupCommand(
                        request.email(), request.passcode(), request.password(), context(httpRequest)))));
    }

    @Operation(
            summary = "Log in",
            description =
                    """
                    Use case AUTH-LOGIN-01. Permission required: none — this is how a principal is established.

                    Failure is uniform by design: an unknown address, a wrong password, and a deactivated account
                    all return the same response, so it cannot be used to discover who holds an account.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The session is open."),
        @ApiResponse(
                responseCode = "400",
                description = "The request is missing a field.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Authentication was refused, without saying which part was wrong.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "429",
                description = "Too many failures within the window.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<SessionContextResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(mapper.toResponse(
                loginUseCase.execute(new LoginCommand(request.email(), request.password(), context(httpRequest)))));
    }

    @Operation(
            summary = "Log out",
            description =
                    """
                    Use case AUTH-LOGOUT-01. Permission required: SESSION_END_OWN, satisfied by construction
                    in the use case rather than checked by an endpoint annotation
                    (DECISION-PERMISSION-ENFORCEMENT-POINT-01; AUTH_02_ACCESS_CONTROL's enforcement-point
                    table states this for every permission).

                    The use case ends the session the caller holds and no other, which is what scopes the
                    permission to its own resource. It does not test whether the principal holds the
                    permission, because every role does and a caller with no session must still succeed:
                    annotating the endpoint would refuse exactly the case the next paragraph requires to
                    return 204.

                    Idempotent: logging out of an already-expired session succeeds, because the caller ends up
                    unauthenticated either way.
                    """)
    @ApiResponse(responseCode = "204", description = "The caller is unauthenticated.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        logoutUseCase.execute(new LogoutCommand(context(httpRequest)));
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Re-authenticate before a sensitive action",
            description =
                    """
                    Use case AUTH-REAUTH-01. Permission required: CREDENTIAL_REAUTH_OWN, satisfied by
                    construction in the use case rather than checked by an endpoint annotation
                    (DECISION-PERMISSION-ENFORCEMENT-POINT-01; AUTH_02_ACCESS_CONTROL's enforcement-point
                    table states this for every permission).

                    It opens no session and creates no identity. It raises the assurance of the session the
                    caller already holds, for a bounded window, so that an action marked sensitive may
                    proceed. The elevation is bound to this session alone and dies with it.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The session is elevated until the instant returned."),
        @ApiResponse(
                responseCode = "400",
                description = "The password is missing.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description =
                        """
                        The password is wrong, or there is no session to elevate. The envelope is present only \
                        in the first case: a caller with no session is refused by the security chain before this \
                        endpoint runs, and that refusal carries a status and no body.""",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "429",
                description = "Too many failures within the window.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/reauthenticate")
    public ResponseEntity<ReauthenticateResponse> reauthenticate(
            @Valid @RequestBody ReauthenticateRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(new ReauthenticateResponse(
                reauthenticateUseCase.execute(new ReauthenticateCommand(request.password(), context(httpRequest)))));
    }

    @Operation(
            summary = "Change the caller's own password",
            description =
                    """
                    Use case AUTH-CHANGE-PASSWORD-01. Permission required: CREDENTIAL_CHANGE_OWN, satisfied by
                    construction in the use case rather than checked by an endpoint annotation
                    (DECISION-PERMISSION-ENFORCEMENT-POINT-01).

                    A sensitive action: the session must be inside its re-authentication window
                    (AUTH-REAUTH-01) or the caller is challenged first. On success the new hash is stored and
                    the caller's other sessions are ended in the same transaction, so a password changed in
                    response to a suspected compromise actually cuts the other holder off. The caller's own
                    session survives.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "The password is changed and the other sessions are ended."),
        @ApiResponse(
                responseCode = "400",
                description =
                        "The new password failed the policy or is the one already in use; the unmet rule is named.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "There is no live session. Refused by the security chain, so this one carries no body."),
        @ApiResponse(
                responseCode = "403",
                description = "The session is not re-authenticated; confirm the password first.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        changePasswordUseCase.execute(new ChangePasswordCommand(request.newPassword(), context(httpRequest)));
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Read the caller's own session context",
            description =
                    """
                    Supports AUTH-LOGIN-01. Permission required: none beyond an active session; it returns the
                    caller's own context and never anyone else's.

                    A refreshed browser holds the session cookie but knows nothing about whom it belongs to, so it
                    cannot decide whether to render the application or the login screen. This answers that.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The caller's identity, permissions, and landing target."),
        @ApiResponse(responseCode = "401", description = "There is no live session.")
    })
    @GetMapping("/session")
    public ResponseEntity<SessionContextResponse> currentSession() {
        return viewSessionContextUseCase
                .execute()
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private ClientContext context(HttpServletRequest request) {
        return new ClientContext(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
