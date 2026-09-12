package com.flowops.workspace.api;

import com.flowops.shared.web.ErrorResponse;
import com.flowops.workspace.api.dto.AcceptInvitationRequest;
import com.flowops.workspace.api.dto.AcceptInvitationResponse;
import com.flowops.workspace.api.dto.DeactivatePersonResponse;
import com.flowops.workspace.api.dto.EditProfileRequest;
import com.flowops.workspace.api.dto.EditProfileResponse;
import com.flowops.workspace.api.dto.ErasePersonRequest;
import com.flowops.workspace.api.dto.ErasePersonResponse;
import com.flowops.workspace.api.dto.ErasurePreviewResponse;
import com.flowops.workspace.api.dto.InvitationPreviewResponse;
import com.flowops.workspace.api.dto.InvitationResponse;
import com.flowops.workspace.api.dto.InvitePersonRequest;
import com.flowops.workspace.api.dto.OwnDataResponse;
import com.flowops.workspace.api.dto.PeopleResponse;
import com.flowops.workspace.api.dto.ReassignPreviewResponse;
import com.flowops.workspace.api.dto.ReassignReportingLineRequest;
import com.flowops.workspace.api.dto.ReassignReportingLineResponse;
import com.flowops.workspace.api.dto.RevokeInvitationResponse;
import com.flowops.workspace.api.dto.SetupWorkspaceRequest;
import com.flowops.workspace.api.dto.UpdateWorkspaceSettingsRequest;
import com.flowops.workspace.api.dto.WorkspaceSettingsResponse;
import com.flowops.workspace.api.dto.WorkspaceSetupPrefillResponse;
import com.flowops.workspace.api.dto.WorkspaceSetupResponse;
import com.flowops.workspace.api.mapper.OwnDataDtoMapper;
import com.flowops.workspace.api.mapper.WorkspaceDtoMapper;
import com.flowops.workspace.application.acceptinvite.AcceptInvitationCommand;
import com.flowops.workspace.application.acceptinvite.AcceptInvitationResult;
import com.flowops.workspace.application.acceptinvite.AcceptInvitationUseCase;
import com.flowops.workspace.application.acceptinvite.DeclineInvitationUseCase;
import com.flowops.workspace.application.acceptinvite.ViewInvitationQuery;
import com.flowops.workspace.application.acceptinvite.ViewInvitationResult;
import com.flowops.workspace.application.acceptinvite.ViewInvitationUseCase;
import com.flowops.workspace.application.configureworkspace.ConfigureWorkspaceCommand;
import com.flowops.workspace.application.configureworkspace.ConfigureWorkspaceUseCase;
import com.flowops.workspace.application.configureworkspace.ViewWorkspaceSettingsUseCase;
import com.flowops.workspace.application.configureworkspace.WorkspaceSettingsView;
import com.flowops.workspace.application.deactivateperson.DeactivatePersonCommand;
import com.flowops.workspace.application.deactivateperson.DeactivatePersonResult;
import com.flowops.workspace.application.deactivateperson.DeactivatePersonUseCase;
import com.flowops.workspace.application.editownprofile.EditOwnProfileCommand;
import com.flowops.workspace.application.editownprofile.EditOwnProfileResult;
import com.flowops.workspace.application.editownprofile.EditOwnProfileUseCase;
import com.flowops.workspace.application.eraseperson.ErasePersonCommand;
import com.flowops.workspace.application.eraseperson.ErasePersonResult;
import com.flowops.workspace.application.eraseperson.ErasePersonUseCase;
import com.flowops.workspace.application.eraseperson.PreviewErasureQuery;
import com.flowops.workspace.application.eraseperson.PreviewErasureResult;
import com.flowops.workspace.application.eraseperson.PreviewErasureUseCase;
import com.flowops.workspace.application.exportowndata.ExportOwnDataUseCase;
import com.flowops.workspace.application.inviteperson.InvitePersonCommand;
import com.flowops.workspace.application.inviteperson.InvitePersonResult;
import com.flowops.workspace.application.inviteperson.InvitePersonUseCase;
import com.flowops.workspace.application.reassignreportingline.PreviewReassignQuery;
import com.flowops.workspace.application.reassignreportingline.PreviewReassignResult;
import com.flowops.workspace.application.reassignreportingline.PreviewReassignUseCase;
import com.flowops.workspace.application.reassignreportingline.ReassignReportingLineCommand;
import com.flowops.workspace.application.reassignreportingline.ReassignReportingLineResult;
import com.flowops.workspace.application.reassignreportingline.ReassignReportingLineUseCase;
import com.flowops.workspace.application.revokeinvitation.RevokeInvitationCommand;
import com.flowops.workspace.application.revokeinvitation.RevokeInvitationResult;
import com.flowops.workspace.application.revokeinvitation.RevokeInvitationUseCase;
import com.flowops.workspace.application.setupworkspace.SetupWorkspaceCommand;
import com.flowops.workspace.application.setupworkspace.SetupWorkspaceUseCase;
import com.flowops.workspace.application.viewowndata.ViewOwnDataQuery;
import com.flowops.workspace.application.viewowndata.ViewOwnDataUseCase;
import com.flowops.workspace.application.viewpeople.ViewPeopleUseCase;
import com.flowops.workspace.application.viewsetupprefill.ViewSetupPrefillUseCase;
import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.InvitationId;
import com.flowops.workspace.domain.model.MembershipId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspace")
@Tag(name = "Workspace", description = "The company inside the installation, and the parameters it is measured by.")
public class WorkspaceController {
    private final ViewSetupPrefillUseCase viewSetupPrefillUseCase;
    private final InvitePersonUseCase invitePersonUseCase;
    private final SetupWorkspaceUseCase setupWorkspaceUseCase;
    private final ViewPeopleUseCase viewPeopleUseCase;
    private final RevokeInvitationUseCase revokeInvitationUseCase;
    private final ReassignReportingLineUseCase reassignReportingLineUseCase;
    private final PreviewReassignUseCase previewReassignUseCase;
    private final ViewInvitationUseCase viewInvitationUseCase;
    private final AcceptInvitationUseCase acceptInvitationUseCase;
    private final DeclineInvitationUseCase declineInvitationUseCase;
    private final DeactivatePersonUseCase deactivatePersonUseCase;
    private final PreviewErasureUseCase previewErasureUseCase;
    private final ErasePersonUseCase erasePersonUseCase;
    private final ViewOwnDataUseCase viewOwnDataUseCase;
    private final ExportOwnDataUseCase exportOwnDataUseCase;
    private final EditOwnProfileUseCase editOwnProfileUseCase;
    private final ViewWorkspaceSettingsUseCase viewWorkspaceSettingsUseCase;
    private final ConfigureWorkspaceUseCase configureWorkspaceUseCase;
    private final WorkspaceDtoMapper mapper;
    private final OwnDataDtoMapper ownDataMapper;

    public WorkspaceController(
            ViewSetupPrefillUseCase viewSetupPrefillUseCase,
            InvitePersonUseCase invitePersonUseCase,
            SetupWorkspaceUseCase setupWorkspaceUseCase,
            ViewPeopleUseCase viewPeopleUseCase,
            RevokeInvitationUseCase revokeInvitationUseCase,
            ReassignReportingLineUseCase reassignReportingLineUseCase,
            PreviewReassignUseCase previewReassignUseCase,
            ViewInvitationUseCase viewInvitationUseCase,
            AcceptInvitationUseCase acceptInvitationUseCase,
            DeclineInvitationUseCase declineInvitationUseCase,
            DeactivatePersonUseCase deactivatePersonUseCase,
            PreviewErasureUseCase previewErasureUseCase,
            ErasePersonUseCase erasePersonUseCase,
            ViewOwnDataUseCase viewOwnDataUseCase,
            ExportOwnDataUseCase exportOwnDataUseCase,
            EditOwnProfileUseCase editOwnProfileUseCase,
            ViewWorkspaceSettingsUseCase viewWorkspaceSettingsUseCase,
            ConfigureWorkspaceUseCase configureWorkspaceUseCase,
            WorkspaceDtoMapper mapper,
            OwnDataDtoMapper ownDataMapper) {
        this.viewSetupPrefillUseCase = viewSetupPrefillUseCase;
        this.invitePersonUseCase = invitePersonUseCase;
        this.setupWorkspaceUseCase = setupWorkspaceUseCase;
        this.viewPeopleUseCase = viewPeopleUseCase;
        this.revokeInvitationUseCase = revokeInvitationUseCase;
        this.reassignReportingLineUseCase = reassignReportingLineUseCase;
        this.previewReassignUseCase = previewReassignUseCase;
        this.viewInvitationUseCase = viewInvitationUseCase;
        this.acceptInvitationUseCase = acceptInvitationUseCase;
        this.declineInvitationUseCase = declineInvitationUseCase;
        this.deactivatePersonUseCase = deactivatePersonUseCase;
        this.previewErasureUseCase = previewErasureUseCase;
        this.erasePersonUseCase = erasePersonUseCase;
        this.viewOwnDataUseCase = viewOwnDataUseCase;
        this.exportOwnDataUseCase = exportOwnDataUseCase;
        this.editOwnProfileUseCase = editOwnProfileUseCase;
        this.viewWorkspaceSettingsUseCase = viewWorkspaceSettingsUseCase;
        this.configureWorkspaceUseCase = configureWorkspaceUseCase;
        this.mapper = mapper;
        this.ownDataMapper = ownDataMapper;
    }

    @Operation(
            summary = "Read what the setup screen needs before anything is typed",
            description =
                    """
                    Use case WORKSPACE-SETUP-01. Permission required: none of its own — the subject is always the
                    caller's own installation, so the rule holds by construction in the use case rather than by an
                    endpoint annotation (DECISION-PERMISSION-ENFORCEMENT-POINT-01; WORKSPACE_02_ACCESS_CONTROL
                    carries the enforcement point for every permission this feature defines).

                    It exists so the timezone is never an empty select. A person configuring a workshop at minute
                    two cannot be expected to care which zone they are in, and an empty list gets a wrong answer
                    where a detected value gets a right one — while a wrong zone produces no error at all and
                    quietly skews every elapsed time the product later computes.

                    The zone returned here is the installation's own and is a fallback: the browser detects the
                    real one, and the list is what its answer is checked against.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Whether setup is done, a suggested zone, and the valid set."),
        @ApiResponse(responseCode = "401", description = "The caller holds no session.")
    })
    @GetMapping("/setup")
    public ResponseEntity<WorkspaceSetupPrefillResponse> setupPrefill() {
        return ResponseEntity.ok(mapper.toResponse(viewSetupPrefillUseCase.execute()));
    }

    @Operation(
            summary = "Set the workspace up",
            description =
                    """
                    Use case WORKSPACE-SETUP-01. Permission required: none of its own, as above — this acts on the
                    caller's own installation and their own account, and names neither in the request.

                    In one transaction it sets the owner's display name, names the workspace, records its use and
                    timezone, marks setup complete on the AUTH account through a declared port, and appends a
                    workspace-setup-completed event. A failure anywhere leaves setup incomplete and nothing
                    stored, so an interrupted setup is recovered by signing in again rather than leaving a
                    half-configured installation.

                    Called again after completion it changes nothing, appends nothing, and answers with the
                    workspace as it stands: reaching this twice is a routing accident, not an error.

                    Work or personal is recorded and acted on by nothing. No feature may condition behaviour on it
                    without a new decision.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The workspace is set up, and the landing target is where the owner goes next."),
        @ApiResponse(
                responseCode = "400",
                description = "A required field is empty, or the timezone is not one we recognise; the field is named.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "The caller holds no session."),
        @ApiResponse(
                responseCode = "403",
                description = "Setting this workspace up is not this caller's to do.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/setup")
    public ResponseEntity<WorkspaceSetupResponse> setUpWorkspace(@Valid @RequestBody SetupWorkspaceRequest request) {
        return ResponseEntity.ok(mapper.toResponse(setupWorkspaceUseCase.execute(new SetupWorkspaceCommand(
                request.ownerName(), request.workspaceName(), request.use(), request.timezone()))));
    }

    @Operation(
            summary = "Invite a person into the workspace (WORKSPACE-INVITE-01)",
            description =
                    """
                    Creates an invitation carrying a role and a reporting line, and sends it once the
                    transaction commits. Requires PERSON_INVITE, which in this pass the owner alone
                    holds — manager invitations and the owner-approval switch are specified and
                    deferred together (DECISION-MVP-TRIAGE-01).

                    The single-use token is never returned by this or any endpoint, and never logged.

                    Refusals carry the specific bound that was violated in `code`, because three of
                    them answer 409 and the status alone cannot tell a caller which rule stopped them:
                    ALREADY_MEMBER, DEACTIVATED_MEMBER, DUPLICATE_INVITATION, DECLINE_WINDOW,
                    RATE_LIMIT, MANAGER_INACTIVE, SELF_INVITATION, ROLE_CEILING.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The invitation was created, and will be sent after commit."),
        @ApiResponse(
                responseCode = "400",
                description = "The body is malformed; `details` names the field and the rule it broke."),
        @ApiResponse(responseCode = "401", description = "The caller holds no session."),
        @ApiResponse(responseCode = "403", description = "The caller may not invite, or not at that role."),
        @ApiResponse(
                responseCode = "409",
                description = "The address is already a member, a deactivated member, or already invited."),
        @ApiResponse(
                responseCode = "422",
                description = "The address declined recently, the manager is inactive, or it is the caller's own."),
        @ApiResponse(responseCode = "429", description = "The invitation limit for this window is reached.")
    })
    @PostMapping("/invitations")
    @PreAuthorize("hasAuthority('PERSON_INVITE')")
    public ResponseEntity<InvitationResponse> invitePerson(@Valid @RequestBody InvitePersonRequest request) {
        InvitePersonResult result = invitePersonUseCase.execute(new InvitePersonCommand(
                EmailAddress.of(request.emailAddress()), request.role(), MembershipId.of(request.managerId())));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new InvitationResponse(
                        result.id().value(),
                        result.email().value(),
                        result.intendedRole().name(),
                        result.intendedManager().value(),
                        result.state().name(),
                        result.expiresAt()));
    }

    @Operation(
            summary = "Withdraw an invitation before it is accepted",
            description =
                    """
                    Use case WORKSPACE-REVOKE-INVITE-01. Permission required: `INVITATION_REVOKE_ANY` **or**
                    `INVITATION_REVOKE_OWN`.

                    **Two permissions on one endpoint, and the split is deliberate.** The annotation answers *may this
                    person revoke at all*; the use case answers *may they revoke **this** one*, because
                    `INVITATION_REVOKE_OWN` is scoped to invitations the caller created and that depends on the request
                    (DECISION-PERMISSION-ENFORCEMENT-POINT-01; the same split WORKSPACE_02_ACCESS_CONTROL records for
                    `PERSON_INVITE`). An annotation naming only the unrestricted permission would refuse a manager
                    holding the scoped one before the use case could apply the scope at all.

                    **An invitation already revoked, declined, refused or expired answers 200 and changes nothing.**
                    The caller's intent -- that this invitation cannot be accepted -- is already true, and an error
                    would report a problem where there is none. The response carries the state actually in force rather
                    than `REVOKED`, and `revokedAt` is absent when the call changed nothing.

                    **An invitation somebody has already accepted is refused**, because this is the one case where
                    success would be actively misleading: the caller would believe they had removed somebody who is
                    still working. The message names that person and names deactivation as the way to end their access.

                    Revocation is immediate and total. The token is checked against the invitation's live state at
                    acceptance, never against anything cached, and no response here carries the token or its hash.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Withdrawn, or already beyond reach and unchanged."),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description =
                        "NOT_PERMITTED holds neither permission; NOT_YOURS holds the scoped one and did not create it.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "INVITATION_NOT_FOUND.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ALREADY_ACCEPTED; the message names the person and names deactivation.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/invitations/{id}/revoke")
    @PreAuthorize("hasAnyAuthority('INVITATION_REVOKE_ANY', 'INVITATION_REVOKE_OWN')")
    public ResponseEntity<RevokeInvitationResponse> revokeInvitation(@PathVariable UUID id) {
        RevokeInvitationResult result =
                revokeInvitationUseCase.execute(new RevokeInvitationCommand(InvitationId.of(id)));

        return ResponseEntity.ok(new RevokeInvitationResponse(
                result.id().value(), result.email().value(), result.state().name(), result.revokedAt()));
    }

    @Operation(
            summary = "See what an invitation offers, before agreeing to anything",
            description =
                    """
                    Use case AUTH-ACCEPT-INVITE-01, main scenario steps 1 and 2. **No permission and no
                    session**: the person holding the link is not yet anybody in this product, and the token
                    is the entire authorisation. It is the only endpoint in WORKSPACE with no `@PreAuthorize`,
                    and that absence is the design rather than an omission.

                    Every one of the five ways a token can be unusable -- unknown, expired, already used,
                    revoked, or belonging to an invitation still awaiting approval -- answers `410` with the
                    same code and an empty `details`. Telling them apart would confirm to whoever holds a
                    token that an address is under consideration (criterion 5).

                    The consent text travels whole rather than as a link, and its version is the hash of it
                    (`DECISION-CONSENT-RECORD-01`). `manager.reassigned` is extension 1b: when the named
                    manager was deactivated while the invitation was pending, the person shown is the first
                    active one above them, and the flag says so rather than substituting quietly.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "What this invitation offers."),
        @ApiResponse(
                responseCode = "410",
                description = "INVITATION_NOT_USABLE, for all five causes alike.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/invitations/{token}")
    public ResponseEntity<InvitationPreviewResponse> viewInvitation(
            @PathVariable String token, @RequestHeader(name = "Accept-Language", required = false) String language) {
        ViewInvitationResult result =
                viewInvitationUseCase.execute(new ViewInvitationQuery(token, languageOf(language)));

        return ResponseEntity.ok(new InvitationPreviewResponse(
                result.workspaceName(),
                result.role().name(),
                new InvitationPreviewResponse.ManagerResponse(result.managerName(), result.managerReassigned()),
                new InvitationPreviewResponse.InviterResponse(result.inviterName()),
                new InvitationPreviewResponse.ConsentResponse(result.consentVersion(), result.consentText())));
    }

    @Operation(
            summary = "Accept an invitation and join (AUTH-ACCEPT-INVITE-01)",
            description =
                    """
                    The only write in this product reachable without a session. The token in the path is
                    the entire authorisation, so there is no permission to require and no principal to
                    require it of.

                    In one transaction it creates the account carrying the name given, the membership
                    with the invitation's role and manager, the consent record naming the version that
                    was actually read, and the session -- and marks the invitation accepted. If any part
                    fails, none of it exists and the invitation is still usable (criterion 3).

                    Unlike the read on this path, this is a POST, so the cross-site token applies: it is
                    minted by the GET that renders the screen and echoed back in `X-XSRF-TOKEN`.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Joined, and authenticated."),
        @ApiResponse(
                responseCode = "400",
                description = "PASSWORD_POLICY_VIOLATION naming the unmet rule, or DISPLAY_NAME_REQUIRED.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ALREADY_MEMBER for an address that already holds an account,"
                        + " or CONSENT_VERSION_STALE if the words changed while they were reading.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "410",
                description = "INVITATION_NOT_USABLE, for all five causes alike.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/invitations/{token}/accept")
    public ResponseEntity<AcceptInvitationResponse> acceptInvitation(
            @PathVariable String token,
            @Valid @RequestBody AcceptInvitationRequest request,
            @RequestHeader(name = "Accept-Language", required = false) String language) {
        AcceptInvitationResult result = acceptInvitationUseCase.execute(new AcceptInvitationCommand(
                token,
                request.displayName(),
                request.password(),
                request.consentAccepted(),
                request.consentVersion(),
                languageOf(language)));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AcceptInvitationResponse(
                        result.userId(),
                        result.email(),
                        result.accountState(),
                        result.permissions(),
                        result.landingTarget()));
    }

    @Operation(
            summary = "Decline an invitation (AUTH-ACCEPT-INVITE-01 extension 3a)",
            description =
                    """
                    A real outcome rather than an error. The invitation moves to declined and the moment
                    is kept, because the reconsideration window is measured from it. No account, no
                    membership and no consent record are created, and nothing personal is retained
                    beyond the address the invitation already held, the outcome, and the time.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Declined; nothing was created."),
        @ApiResponse(
                responseCode = "410",
                description = "INVITATION_NOT_USABLE, for all five causes alike.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/invitations/{token}/decline")
    public ResponseEntity<Void> declineInvitation(@PathVariable String token) {
        declineInvitationUseCase.execute(token);
        return ResponseEntity.noContent().build();
    }

    private static String languageOf(String header) {
        if (header == null || header.isBlank()) {
            return "en";
        }
        return header.split(",")[0].split(";")[0].split("-")[0].trim();
    }

    @Operation(
            summary = "See what moving somebody would do, before doing it",
            description =
                    """
                    Use case WORKSPACE-EDIT-REPORTING-LINE-01, main scenario step 3. Permission required:
                    `REPORTING_LINE_EDIT`.

                    It writes nothing and takes no lock. A preview that held the structure while somebody read a
                    dialog would block every other structural edit for as long as they took to decide -- so a preview
                    can go stale, and the action re-validates under the lock rather than trusting what the screen was
                    told.

                    `movingWithThem` is extension 3a: moving one person can silently relocate a third of the company,
                    so the confirmation names them rather than only counting them. The count is the list's length and
                    is not sent separately, because two fields carrying one fact is two places for it to be wrong.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "What the move would change."),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content),
        @ApiResponse(responseCode = "403", description = "Without REPORTING_LINE_EDIT.", content = @Content),
        @ApiResponse(responseCode = "404", description = "MEMBERSHIP_NOT_FOUND.", content = @Content)
    })
    @GetMapping("/people/{membershipId}/manager/preview")
    @PreAuthorize("hasAuthority('REPORTING_LINE_EDIT')")
    public ResponseEntity<ReassignPreviewResponse> previewReassign(
            @PathVariable UUID membershipId, @RequestParam UUID proposedManagerId) {
        PreviewReassignResult result = previewReassignUseCase.execute(
                new PreviewReassignQuery(MembershipId.of(membershipId), MembershipId.of(proposedManagerId)));

        return ResponseEntity.ok(new ReassignPreviewResponse(
                result.personName(),
                result.formerManagerName().orElse(null),
                result.newManagerName(),
                result.movingWithThem().stream()
                        .map(person -> new ReassignPreviewResponse.MovingPersonResponse(
                                person.membership().value(), person.displayName()))
                        .toList(),
                result.alreadyTheirManager()));
    }

    @Operation(
            summary = "Change who a person reports to",
            description =
                    """
                    Use case WORKSPACE-EDIT-REPORTING-LINE-01. Permission required: `REPORTING_LINE_EDIT`, and it is
                    **owner-only**.

                    **That restriction is the protection rather than a default.** WORKSPACE_UC_04: *"moving a person
                    under yourself grants visibility over their work, which is an escalation that appears nowhere in
                    the permission matrix because no permission changed."* Widening it later is not a configuration
                    change -- it needs a subtree-containment check whose failure mode is silent.

                    Every refusal names its own invariant, and six of them answer 422, so the **code** is what tells
                    them apart. `CYCLE` carries the path that closes the loop in `details`, because it is the only
                    refusal in this feature a reasonable person triggers by accident and it has to explain itself
                    rather than report a violation.

                    An already-correct proposal answers 200 with `changed: false`, writes nothing, and appends no
                    event -- the log records what happened, and nothing did.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Moved, or already true and unchanged."),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "MEMBERSHIP_NOT_FOUND.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description =
                        "CYCLE, SELF_MANAGER, OWNER_HAS_NO_MANAGER, SUBJECT_INACTIVE, MANAGER_INACTIVE or MANAGER_NOT_ELIGIBLE.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/people/{membershipId}/manager")
    @PreAuthorize("hasAuthority('REPORTING_LINE_EDIT')")
    public ResponseEntity<ReassignReportingLineResponse> reassignReportingLine(
            @PathVariable UUID membershipId, @Valid @RequestBody ReassignReportingLineRequest request) {
        ReassignReportingLineResult result = reassignReportingLineUseCase.execute(new ReassignReportingLineCommand(
                MembershipId.of(membershipId), MembershipId.of(request.proposedManagerId())));

        return ResponseEntity.ok(new ReassignReportingLineResponse(
                result.membership().value(),
                result.formerManager().map(MembershipId::value).orElse(null),
                result.newManager().value(),
                result.changed()));
    }

    @Operation(
            summary = "End a person's access",
            description =
                    """
                    Use case WORKSPACE-DEACTIVATE-PERSON-01. Permission required: `PERSON_DEACTIVATE`, and it is
                    **owner-only and non-delegable** -- delegation lends approval authority and must not reach a
                    permission that acts on another person's access.

                    In one transaction the membership is deactivated, the person's **direct reports** re-parent to
                    that person's own manager, every session they hold ends, and the event is appended. The subtree
                    moves up one level; it is not flattened. Their authored work is untouched and still theirs.

                    **`ONLY_OWNER` is the refusal that matters.** Nothing promotes anybody to owner and registration
                    closes once one exists, so deactivating the last active owner would leave the installation
                    permanently unreachable. It is a distinct code rather than a general refusal because a caller who
                    meets it has done nothing wrong.

                    An already-deactivated person answers 200 with `changed: false`, writes nothing and appends no
                    event -- the log records what happened, and nothing did.

                    **Deferred, and stated rather than discovered** (`WORKSPACE_REQ_BLOCKER_05`): this does not end
                    delegations, revoke or re-point the person's pending invitations, or reassign their open work.
                    Each waits for the use case that creates the thing it would act on.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Deactivated, or already so and unchanged."),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "MEMBERSHIP_NOT_FOUND.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "ONLY_OWNER -- the installation would be left unreachable.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/people/{membershipId}/deactivate")
    @PreAuthorize("hasAuthority('PERSON_DEACTIVATE')")
    public ResponseEntity<DeactivatePersonResponse> deactivatePerson(@PathVariable UUID membershipId) {
        DeactivatePersonResult result =
                deactivatePersonUseCase.execute(new DeactivatePersonCommand(MembershipId.of(membershipId)));

        return ResponseEntity.ok(new DeactivatePersonResponse(
                result.person().value(),
                result.changed(),
                result.reportsMoved().stream().map(MembershipId::value).toList(),
                result.reportsMovedTo().map(MembershipId::value).orElse(null)));
    }

    @Operation(
            summary = "See how the business is measured (WORKSPACE-CONFIGURE-01)",
            description =
                    """
                    Permission required: `WORKSPACE_CONFIGURE`, **owner-only and not delegable**. Enforced at
                    the endpoint rather than in the use case because the subject is the workspace, not the
                    caller's own anything -- there is no self-scoping here to hold the rule by construction.

                    **This is quietly the most consequential screen in the product.** Every number TRIAGE
                    shows and every nudge AUTOMATION sends is computed against these values, and nothing
                    validates them against reality: a wrong working day produces no error, only confidently
                    incorrect metrics that nobody notices for weeks.

                    `effectiveFrom` is returned because a person asking why a number looks wrong three weeks
                    later is asking exactly that. The screen is never empty -- shipping defaults apply until
                    somebody changes them.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The values in force."),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED -- owner-only.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('WORKSPACE_CONFIGURE')")
    public ResponseEntity<WorkspaceSettingsResponse> viewSettings() {
        return ResponseEntity.ok(settings(viewWorkspaceSettingsUseCase.execute()));
    }

    @Operation(
            summary = "Change how the business is measured",
            description =
                    """
                    Permission required: `WORKSPACE_CONFIGURE`.

                    **Validation is across fields, not per field**, and every refusal names the fields
                    involved. `ESCALATION_INTERVALS_UNORDERED` names *two* positions, because an interval is
                    only wrong relative to its neighbour. `QUIET_HOURS_COVER_THE_DAY` names both ends.
                    `AT_RISK_WINDOW_INVALID` and `NO_WORKING_DAYS` name their own field.

                    **Quiet hours that wrap midnight are the normal case.** Twenty-two to six is accepted and
                    covers the six hours before midnight and the six after; treating it as inverted would
                    either fire escalations through the night or never fire them, and both look like a working
                    system from outside.

                    **The write is effective-dated.** The superseded row keeps its period, so a measurement
                    taken last month still resolves the parameters that were true last month -- without that,
                    adjusting working hours in March silently changes every February measurement, with no
                    error and no test failure.

                    **Submitting values identical to the current ones changes nothing**: no settings row, no
                    event, no change rows, and `changedFields` comes back empty. The record is a history of
                    changes rather than a history of visits (extension 5b).

                    The event carries the actor and the moment; what moved is one row per field in
                    `workspace_settings_change`, with its old and new value. That is the only event payload in
                    the product holding values rather than identifiers, and it is safe because a time, a count
                    and a day mask are facts about a business rather than about a person.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stored, or unchanged with an empty changedFields."),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID -- a value is missing or malformed.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED -- owner-only.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "ESCALATION_INTERVALS_UNORDERED, AT_RISK_WINDOW_INVALID,"
                        + " QUIET_HOURS_COVER_THE_DAY, NO_WORKING_DAYS or UNKNOWN_TIMEZONE.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('WORKSPACE_CONFIGURE')")
    public ResponseEntity<WorkspaceSettingsResponse> updateSettings(
            @Valid @RequestBody UpdateWorkspaceSettingsRequest request) {
        return ResponseEntity.ok(settings(configureWorkspaceUseCase.execute(new ConfigureWorkspaceCommand(
                request.name(),
                request.use(),
                request.timezone(),
                request.workingDays(),
                request.workingHoursStart(),
                request.workingHoursEnd(),
                request.atRiskWindowHours(),
                request.escalationIntervalsHours(),
                request.quietHoursStart(),
                request.quietHoursEnd(),
                request.invitationApprovalRequired(),
                request.closureCoverageThresholdPercent(),
                request.templateIdleWindowDays()))));
    }

    private static WorkspaceSettingsResponse settings(WorkspaceSettingsView view) {
        return new WorkspaceSettingsResponse(
                view.name(),
                view.use(),
                view.timezone(),
                view.workingDays(),
                view.workingHoursStart(),
                view.workingHoursEnd(),
                view.atRiskWindowHours(),
                view.escalationIntervalsHours(),
                view.quietHoursStart(),
                view.quietHoursEnd(),
                view.invitationApprovalRequired(),
                view.closureCoverageThresholdPercent(),
                view.templateIdleWindowDays(),
                view.effectiveFrom(),
                view.changedFields());
    }

    @Operation(
            summary = "See everything held about me (WORKSPACE-VIEW-OWN-DATA-01)",
            description =
                    """
                    The right of access, made real. **There is no parameter naming whose data to return, and
                    that absence is the enforcement** -- `OWN_DATA_VIEW` holds by construction because the
                    subject is the caller and cannot be anybody else (DECISION-PERMISSION-ENFORCEMENT-POINT-01).
                    An endpoint that accepted one would be an access-control decision waiting to be got wrong,
                    and this is not an administrative tool.

                    **The only other person named anywhere in this response is a manager in the caller's own
                    reporting line.** That line is the caller's data -- it is the record of who could see their
                    work -- and every member already reads every name through the directory, so it discloses
                    nothing new. No colleague's address, no task content and no comment text appears at any
                    point.

                    `authored` carries zeroes and is present rather than omitted: nothing in the product is
                    authored yet, and an absent section would read as *we are not telling you*.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The caller's whole file."),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "MEMBERSHIP_NOT_FOUND -- an account with no membership here.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<OwnDataResponse> viewOwnData() {
        return ResponseEntity.ok(ownDataMapper.toResponse(viewOwnDataUseCase.execute(ViewOwnDataQuery.forTheCaller())));
    }

    @Operation(
            summary = "Take a copy of everything held about me",
            description =
                    """
                    Main scenario steps 3 and 4. It returns **the same information as the screen** -- the same
                    use case, the same mapper -- because criterion 3 says so and because two readers of two
                    queries would drift, with the screen right and the file the one nobody checks.

                    Delivered as a JSON attachment: a file a person keeps, not a page they read. It is the one
                    read on this path that **writes**, recording that a copy was produced, by whom and when --
                    which is both what bounds the next request and what makes an owner-produced copy legible as
                    having been made on somebody's behalf.

                    Bounded per person per day (extension 4a). An export reads across every table that holds
                    anything about somebody, so an unbounded loop of them is a denial-of-service vector aimed
                    at the person's own workspace.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The file."),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content),
        @ApiResponse(
                responseCode = "429",
                description = "EXPORT_LIMIT -- several copies already produced today.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/me/export")
    public ResponseEntity<OwnDataResponse> exportOwnData() {
        OwnDataResponse file = ownDataMapper.toResponse(exportOwnDataUseCase.execute(ViewOwnDataQuery.forTheCaller()));
        return asAttachment(file, "my-data.json");
    }

    @Operation(
            summary = "Produce a deactivated person's data on their behalf (extension 3a)",
            description =
                    """
                    **The right of access does not lapse when access does.** A deactivated person cannot sign
                    in -- deliberately, and restoring that would reopen what WORKSPACE-DEACTIVATE-PERSON-01
                    closed -- so the owner produces their file and hands it over.

                    **This is the only path in this use case with a subject parameter**, and it is bounded
                    twice. `PERSON_DEACTIVATE` at the endpoint, which is owner-only and non-delegable and whose
                    population is exactly the people this may reach; and a refusal in the use case for a
                    subject who is **still active**, because somebody with access can ask for their own copy.

                    The permission is borrowed rather than invented: `WORKSPACE_02_ACCESS_CONTROL` defines no
                    permission for this path, and declaring one would be a write into an owner-owned
                    specification. Recorded in `work/IMPL_DECISIONS.md`; if the owner adds
                    `PERSON_DATA_EXPORT`, this annotation is the one line that changes.

                    The file records that it was produced by somebody other than its subject, so the person
                    receiving it can see who made it.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The file, recorded as produced on their behalf."),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "MEMBERSHIP_NOT_FOUND.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "SUBJECT_ACTIVE -- they can still sign in and ask for their own copy.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "429",
                description = "EXPORT_LIMIT.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/people/{membershipId}/export")
    @PreAuthorize("hasAuthority('PERSON_DEACTIVATE')")
    public ResponseEntity<OwnDataResponse> exportOnBehalfOf(@PathVariable UUID membershipId) {
        OwnDataResponse file = ownDataMapper.toResponse(
                exportOwnDataUseCase.execute(ViewOwnDataQuery.onBehalfOf(MembershipId.of(membershipId))));
        return asAttachment(file, "person-data.json");
    }

    @Operation(
            summary = "Change the name others see (WORKSPACE-EDIT-OWN-PROFILE-01)",
            description =
                    """
                    The caller's own name and nothing else. **No parameter names whose profile to change**,
                    which is the enforcement rather than an omission, and no field carries an address --
                    extension 1c: the address is what the invitation, the consent record and the credential are
                    bound to.

                    The name propagates everywhere it is rendered without touching a single authored item,
                    because every reference in the product resolves through the person record rather than
                    storing a copy. That is the same property erasure depends on, seen from the other side.

                    **The event names the person, the actor and the moment, and carries neither the old name
                    nor the new one.** Criterion 1 asked for both values and could not have them: an event
                    holding somebody's previous name would survive their erasure permanently. Raised as
                    `WORKSPACE_REQ_BLOCKER_06` and ruled option A by the owner on 2026-08-10, with what that
                    costs stated in the use case.

                    Submitting the name already held answers 200 with `changed: false`, writes nothing and
                    appends no event (extension 1b).
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stored, or already theirs and unchanged."),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID or MEMBER_NAME_REQUIRED -- there is no fallback better than"
                        + " the previous name.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content)
    })
    @PutMapping("/me/profile")
    public ResponseEntity<EditProfileResponse> editOwnProfile(@Valid @RequestBody EditProfileRequest request) {
        EditOwnProfileResult result = editOwnProfileUseCase.execute(new EditOwnProfileCommand(request.displayName()));
        return ResponseEntity.ok(new EditProfileResponse(result.displayName(), result.changed()));
    }

    private ResponseEntity<OwnDataResponse> asAttachment(OwnDataResponse file, String name) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(file);
    }

    @Operation(
            summary = "See what erasing somebody would destroy, before doing it",
            description =
                    """
                    Use case WORKSPACE-ERASE-PERSON-01, main scenario step 2. Permission required:
                    `PERSON_ERASE`, **owner-only and never delegable** -- erasure is the one operation where a
                    mistaken grant of authority cannot be corrected afterwards.

                    It writes nothing and requires **no re-authentication**. The elevation guards the act, not
                    the reading of what the act would do: challenging somebody before they may see the
                    consequences would put the barrier in front of the information rather than in front of the
                    destruction.

                    `destroys` and `survives` are keys, not sentences, so the words are translated with
                    everything else. The dialog additionally states -- always, and not from any flag here --
                    that **free text is not rewritten**: a comment reading *ask Maria about the invoice* still
                    names Maria afterwards. Somebody promised complete erasure and given partial erasure has
                    been misled, and this is the last screen that can say so.

                    `refusal` reports the barriers rather than throwing on them, because an owner who opens a
                    still-active person needs an explanation on the screen they are already looking at.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "What erasure would do."),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "MEMBERSHIP_NOT_FOUND.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/people/{membershipId}/erasure")
    @PreAuthorize("hasAuthority('PERSON_ERASE')")
    public ResponseEntity<ErasurePreviewResponse> previewErasure(@PathVariable UUID membershipId) {
        PreviewErasureResult result =
                previewErasureUseCase.execute(new PreviewErasureQuery(MembershipId.of(membershipId)));

        return ResponseEntity.ok(new ErasurePreviewResponse(
                result.person().value(),
                result.displayName().orElse(null),
                result.deactivatedAt().orElse(null),
                result.eligible(),
                result.refusal().orElse(null),
                result.destroys(),
                result.survives(),
                result.alreadyErased()));
    }

    @Operation(
            summary = "Erase a person and anonymise their work (WORKSPACE-ERASE-PERSON-01)",
            description =
                    """
                    The only irreversible operation in the product. Permission required: `PERSON_ERASE`,
                    owner-only and never delegable.

                    **Three barriers, and each removes a different failure.** The person must already be
                    deactivated, which removes the accidental single click. The session must hold a current
                    re-authentication, which removes the unattended session. And the caller must type the
                    person's name, which removes the wrong row. None of them substitutes for the others, so
                    each answers with its own code.

                    In one transaction the name, the address, the credential and the session metadata are
                    destroyed, the membership is marked erased, and the event is appended. **Nothing else is
                    touched, and that is the design rather than a limit**: every feature stores a person
                    identifier and renders the name from the person record, so destroying that record makes
                    everything they authored read *Former member* with no call into any other feature. The
                    consent record survives with the person anonymised -- it is the only evidence the product
                    ever had permission to measure them at all.

                    An already-erased person answers 200 with `changed: false`. There is nothing left to
                    destroy, and an error would suggest something is wrong when the caller's intent is already
                    true.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Erased, or already so and unchanged."),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or REAUTHENTICATION_REQUIRED when the elevation has expired.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "MEMBERSHIP_NOT_FOUND.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "SUBJECT_ACTIVE, ONLY_OWNER, or NAME_MISMATCH -- one code per barrier.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/people/{membershipId}/erase")
    @PreAuthorize("hasAuthority('PERSON_ERASE')")
    public ResponseEntity<ErasePersonResponse> erasePerson(
            @PathVariable UUID membershipId, @Valid @RequestBody ErasePersonRequest request) {
        ErasePersonResult result =
                erasePersonUseCase.execute(new ErasePersonCommand(MembershipId.of(membershipId), request.typedName()));

        return ResponseEntity.ok(new ErasePersonResponse(
                result.person().value(), result.opaqueIdentifier().value(), result.changed()));
    }

    @Operation(
            summary = "See the people and the reporting line",
            description =
                    """
                    Use case WORKSPACE-VIEW-PEOPLE-01. Permission required: `PEOPLE_VIEW`, held by the owner, managers
                    and employees alike — everybody in a workspace needs to know who is here and who to ask.

                    **One thing differs by viewer, and it is enforced in the use case rather than here.** Pending
                    invitations are returned only to a caller holding `PERSON_INVITE`, and a manager who holds it sees
                    only the invitations they created themselves. That rule depends on the caller's role and on who
                    created each row, which no annotation can express (DECISION-PERMISSION-ENFORCEMENT-POINT-01;
                    WORKSPACE_02_ACCESS_CONTROL records the split so a reviewer finding no annotation for it knows to
                    look in the interactor).

                    The filtering happens on the server. A response carrying invitations the viewer may not see, for
                    the interface to hide, has already disclosed them — so for a viewer without `PERSON_INVITE` the
                    `invitations` field is absent from the JSON entirely rather than present and empty.

                    **No address of any member appears**, only display names; and no count, score, average, ranking or
                    comparison of anybody's work appears at any workspace size (CONSTRAINT-METRICS-PRIVACY-01). A
                    directory is the most natural place in the product to add one, which is why the exclusion is a
                    security requirement here rather than a product preference.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description =
                        "The directory, the reporting line, and — for those who may see them — pending invitations."),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "Authenticated, but without PEOPLE_VIEW.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/people")
    @PreAuthorize("hasAuthority('PEOPLE_VIEW')")
    public ResponseEntity<PeopleResponse> people() {
        return ResponseEntity.ok(mapper.toResponse(viewPeopleUseCase.execute()));
    }
}
