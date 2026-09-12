package com.flowops.workspace.api;

import com.flowops.shared.web.ErrorResponse;
import com.flowops.workspace.api.dto.AssignFunctionalRoleRequest;
import com.flowops.workspace.api.dto.CreateFunctionalRoleRequest;
import com.flowops.workspace.api.dto.NameRequest;
import com.flowops.workspace.api.dto.OrganisationResponse;
import com.flowops.workspace.application.assignfunctionalrole.AssignFunctionalRoleUseCase;
import com.flowops.workspace.application.manageorganisation.ManageOrganisationUseCase;
import com.flowops.workspace.application.vieworganisation.ViewOrganisationUseCase;
import com.flowops.workspace.application.vieworganisation.ViewRoleAssignmentsUseCase;
import com.flowops.workspace.domain.model.FunctionalRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspace")
@Tag(name = "Workspace", description = "The company inside the installation, and the parameters it is measured by.")
public class OrganisationController {
    private final ViewOrganisationUseCase viewOrganisationUseCase;
    private final ViewRoleAssignmentsUseCase viewRoleAssignmentsUseCase;
    private final AssignFunctionalRoleUseCase assignFunctionalRoleUseCase;
    private final ManageOrganisationUseCase manageOrganisationUseCase;

    public OrganisationController(
            ViewOrganisationUseCase viewOrganisationUseCase,
            ViewRoleAssignmentsUseCase viewRoleAssignmentsUseCase,
            AssignFunctionalRoleUseCase assignFunctionalRoleUseCase,
            ManageOrganisationUseCase manageOrganisationUseCase) {
        this.viewOrganisationUseCase = viewOrganisationUseCase;
        this.viewRoleAssignmentsUseCase = viewRoleAssignmentsUseCase;
        this.assignFunctionalRoleUseCase = assignFunctionalRoleUseCase;
        this.manageOrganisationUseCase = manageOrganisationUseCase;
    }

    @Operation(
            summary = "Add a department",
            description =
                    """
                    WORKSPACE-MANAGE-ORGANISATION-01. The four a migration ships — Client services, Content,
                    Design, Ads — describe a creative agency, and a workspace that is not one had no way to
                    say what parts it actually has.

                    Needed before a role can be added at all, because a role's department is not nullable.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Added"),
        @ApiResponse(responseCode = "409", description = "One already goes by that name", content = @Content),
        @ApiResponse(responseCode = "403", description = "Requires WORKSPACE_CONFIGURE", content = @Content)
    })
    @PreAuthorize("hasAuthority('WORKSPACE_CONFIGURE')")
    @PostMapping("/departments")
    public ResponseEntity<UUID> createDepartment(@Valid @RequestBody NameRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(manageOrganisationUseCase.createDepartment(request.name()));
    }

    @Operation(summary = "Rename a department", description = "Changes a label. Nothing derives a work type from it.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Renamed"),
        @ApiResponse(responseCode = "409", description = "One already goes by that name", content = @Content),
        @ApiResponse(responseCode = "422", description = "No such department", content = @Content)
    })
    @PreAuthorize("hasAuthority('WORKSPACE_CONFIGURE')")
    @PutMapping("/departments/{departmentId}")
    public ResponseEntity<Void> renameDepartment(
            @PathVariable UUID departmentId, @Valid @RequestBody NameRequest request) {
        manageOrganisationUseCase.renameDepartment(departmentId, request.name());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Remove a department",
            description =
                    """
                    Refused while any role hangs from it. A role's department is not nullable, so the
                    alternatives are orphaning every role beneath it or deleting them — and deleting them
                    silently is the destructive case one level up, where it is harder to notice.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Removed"),
        @ApiResponse(responseCode = "409", description = "Roles still hang from it", content = @Content),
        @ApiResponse(responseCode = "422", description = "No such department", content = @Content)
    })
    @PreAuthorize("hasAuthority('WORKSPACE_CONFIGURE')")
    @DeleteMapping("/departments/{departmentId}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable UUID departmentId) {
        manageOrganisationUseCase.deleteDepartment(departmentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Add a job this business has",
            description =
                    """
                    WORKSPACE-MANAGE-ORGANISATION-01, and it reaches further than an org chart.

                    DISCOVERY derives a bracket's work type from the performer's role NAME and freezes it
                    onto the bracket for ever, so this list is the work vocabulary of the whole observing
                    zone. Without it a workspace outside the six shipped roles produced GENERAL for every
                    person — and once every bracket carries one work type the five-part address collapses,
                    two people's work in one conversation merges into one thread, and the learned process
                    has a single step.

                    Never gated on the taxonomy being tidy. R20: anyone may create a work type and it is
                    never blocked; the safeguard is visibility, not permission.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Added"),
        @ApiResponse(responseCode = "409", description = "A role already goes by that name", content = @Content),
        @ApiResponse(responseCode = "422", description = "No such department", content = @Content)
    })
    @PreAuthorize("hasAuthority('FUNCTIONAL_ROLE_ASSIGN')")
    @PostMapping("/functional-roles")
    public ResponseEntity<FunctionalRole> createRole(@Valid @RequestBody CreateFunctionalRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(manageOrganisationUseCase.createRole(request.name(), request.departmentId()));
    }

    @Operation(
            summary = "Rename a job",
            description =
                    """
                    Work already recorded keeps the work type it was given. A bracket's type is frozen at
                    open precisely so somebody promoted from writer to team lead does not have three years
                    of their content work retroactively relabelled — so this changes what NEW brackets
                    derive and leaves the graph exactly as it stands.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Renamed"),
        @ApiResponse(responseCode = "409", description = "A role already goes by that name", content = @Content),
        @ApiResponse(responseCode = "422", description = "No such role", content = @Content)
    })
    @PreAuthorize("hasAuthority('FUNCTIONAL_ROLE_ASSIGN')")
    @PutMapping("/functional-roles/{roleId}")
    public ResponseEntity<Void> renameRole(@PathVariable UUID roleId, @Valid @RequestBody NameRequest request) {
        manageOrganisationUseCase.renameRole(roleId, request.name());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Remove a job nobody holds",
            description =
                    """
                    Refused while anybody holds it, and the count travels with the refusal.

                    Deleting a held role would blank the assignment behind a person's back, and every
                    bracket they opened afterwards would derive GENERAL — work still recorded and quietly no
                    longer classified, which is the failure this product is least able to notice.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Removed"),
        @ApiResponse(responseCode = "409", description = "People still hold it", content = @Content),
        @ApiResponse(responseCode = "422", description = "No such role", content = @Content)
    })
    @PreAuthorize("hasAuthority('FUNCTIONAL_ROLE_ASSIGN')")
    @DeleteMapping("/functional-roles/{roleId}")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID roleId) {
        manageOrganisationUseCase.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "The departments and the jobs in them",
            description =
                    """
                    WORKSPACE-VIEW-ORGANISATION-01. Any authenticated member may read it, and there is
                    deliberately no permission beyond that: what jobs a business has is not private from
                    the people doing them, and a vocabulary that differed by viewer would make an SOP —
                    a document meant to read the same to everybody — read differently to each of them.
                    Never empty on a migrated database; V64 seeds four departments and six roles.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Every department, with its jobs, in display order"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content)
    })
    @GetMapping("/organisation")
    public ResponseEntity<OrganisationResponse> organisation() {
        return ResponseEntity.ok(OrganisationResponse.of(viewOrganisationUseCase.execute()));
    }

    @Operation(
            summary = "Who holds which job",
            description =
                    """
                    WORKSPACE-ASSIGN-FUNCTIONAL-ROLE-01, read side. Membership identifier to functional
                    role identifier, and a membership absent from the map has no job recorded — which is
                    an ordinary state rather than a gap, exactly as a null on the write side is.

                    Names are deliberately absent. They belong to the people directory, which applies its
                    own scoping and is already on screen wherever this is used; repeating them here would
                    be a second copy of a fact that can then disagree with the first.

                    Authenticated and otherwise unscoped, like the chart beside it: what people do is not
                    private from the people doing it.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Every recorded assignment"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content)
    })
    @GetMapping("/organisation/assignments")
    public ResponseEntity<Map<UUID, UUID>> assignments() {
        return ResponseEntity.ok(viewRoleAssignmentsUseCase.execute());
    }

    @Operation(
            summary = "Record what somebody does",
            description =
                    """
                    WORKSPACE-ASSIGN-FUNCTIONAL-ROLE-01. Requires FUNCTIONAL_ROLE_ASSIGN, held by owner
                    and manager — unlike REPORTING_LINE_EDIT, which is the owner's alone. Moving somebody
                    in the tree changes whose work they may be given and is governance; saying that Daria
                    writes copy describes something already true, and her manager is who knows it.

                    A null functionalRoleId clears the assignment, which is a real state rather than a
                    missing argument. Nothing about a person's work waits on this: an SOP naming an unset
                    role renders *Not recorded*, a gap stated rather than invented.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Recorded"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold FUNCTIONAL_ROLE_ASSIGN",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_FUNCTIONAL_ROLE: that job is not one of this workspace's roles",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/people/{membershipId}/functional-role")
    @PreAuthorize("hasAuthority('FUNCTIONAL_ROLE_ASSIGN')")
    public ResponseEntity<Void> assignFunctionalRole(
            @PathVariable UUID membershipId, @Valid @RequestBody AssignFunctionalRoleRequest request) {
        assignFunctionalRoleUseCase.execute(membershipId, request.functionalRoleId());
        return ResponseEntity.noContent().build();
    }
}
