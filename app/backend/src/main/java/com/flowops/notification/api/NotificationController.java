package com.flowops.notification.api;

import com.flowops.notification.api.dto.InboxResponse;
import com.flowops.notification.api.dto.NotificationRow;
import com.flowops.notification.api.dto.PreferencesPayload;
import com.flowops.notification.api.dto.UnreadCountResponse;
import com.flowops.notification.application.inbox.MarkReadUseCase;
import com.flowops.notification.application.inbox.ViewInboxUseCase;
import com.flowops.notification.application.preferences.PreferencesUseCase;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(
        name = "Notification",
        description = "One person's own notifications and preferences. No route serves anybody else's.")
public class NotificationController {
    private final ViewInboxUseCase viewInbox;
    private final MarkReadUseCase markRead;
    private final PreferencesUseCase preferences;

    public NotificationController(
            ViewInboxUseCase viewInbox, MarkReadUseCase markRead, PreferencesUseCase preferences) {
        this.viewInbox = viewInbox;
        this.markRead = markRead;
        this.preferences = preferences;
    }

    @Operation(
            summary = "What I have been told",
            description =
                    """
                    NOTIFICATION-VIEW-INBOX-01. Requires NOTIFICATION_VIEW_OWN, which everybody holds.

                    UNREAD FIRST, THEN NEWEST FIRST, and read rows are dimmed rather than removed — a
                    read notice is still history. There is no grouping by kind, no filter and no search:
                    a list short enough to need none of those is the design goal, and if it grows past
                    that the fix is fewer notifications rather than more controls.

                    MORE THAN THE WORKSPACE'S DIGEST THRESHOLD RELEASING AT ONE INSTANT BECOME ONE ROW,
                    carrying its items, expandable in place. Escalations are never folded in; they are
                    extracted and delivered individually however many there are.

                    A ROW WHOSE SUBJECT IS GONE CARRIES A NULL subjectId and renders as no longer
                    available — never as stale text describing a task that has since changed. No message
                    body is stored anywhere, so the sentence is composed by the screen from the kind and
                    nothing here has travelled past the access checks that govern the original.

                    THERE IS NO PERSON PARAMETER, and there is no route that has one. An inbox read by
                    somebody else is a performance history containing only the moments something went
                    wrong.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "My notifications, unread first"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content)
    })
    @GetMapping("/notifications")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW_OWN')")
    public ResponseEntity<InboxResponse> inbox() {
        return ResponseEntity.ok(new InboxResponse(
                viewInbox.execute().stream().map(NotificationRow::of).toList()));
    }

    @Operation(
            summary = "How many of mine are waiting",
            description =
                    """
                    NOTIFICATION-VIEW-INBOX-01, for the bell. Requires NOTIFICATION_VIEW_OWN.

                    A PLAIN NUMBER. The screen renders nothing at all when it is zero — the bell stays,
                    the number goes. No red dot, no pulse, no growth animation: those are pressure, and
                    pressure applied to a notification list is what produces bulk dismissal, which takes
                    the escalations with it.

                    Mine, asked by me. A count of somebody else's is the artefact
                    CONSTRAINT-METRICS-PRIVACY-01 refuses, and no route composes one.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "My unread count"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content)
    })
    @GetMapping("/notifications/unread-count")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW_OWN')")
    public ResponseEntity<UnreadCountResponse> unreadCount() {
        return ResponseEntity.ok(new UnreadCountResponse(viewInbox.unreadCount()));
    }

    @Operation(
            summary = "I opened it",
            description =
                    """
                    NOTIFICATION-VIEW-INBOX-01. Requires NOTIFICATION_VIEW_OWN.

                    OPENING A ROW OPENS THE SUBJECT AND MARKS IT READ — two acts, one gesture. A separate
                    acknowledge step is a step people stop taking, and then the unread count reports a
                    backlog nobody has.

                    Idempotent: the first instant is the true one, and pressing again does not move it.

                    A NOTIFICATION THAT IS NOT MINE ANSWERS THE SAME AS ONE THAT DOES NOT EXIST.
                    Distinguishing them would confirm that a colleague was chased about something.

                    THERE IS NO MARK-ALL-READ ROUTE AND THERE WILL NOT BE. It is the control people press
                    instead of reading; a channel that is bulk-dismissed has already failed.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Read"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "NOT_FOUND: no such notification, or it is not the caller's",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/notifications/{id}/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW_OWN')")
    public ResponseEntity<Void> read(@PathVariable UUID id) {
        markRead.execute(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "What interrupts me",
            description =
                    """
                    NOTIFICATION-PREFERENCES-01. Requires NOTIFICATION_VIEW_OWN.

                    FOUR GROUPS: assignment, time, process, weekly. Groups rather than individual
                    notices, because a screen with twenty switches is a screen nobody configures and the
                    person who cannot find the one they want turns everything off.

                    ESCALATION IS ABSENT FROM THIS PAYLOAD — not present and false, absent. A disabled
                    control implies a permission that could be granted, and this one cannot.

                    Absent from storage means enabled, so somebody who has never opened the screen gets
                    four trues.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "My four switches"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content)
    })
    @GetMapping("/notification-preferences")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW_OWN')")
    public ResponseEntity<PreferencesPayload> preferences() {
        return ResponseEntity.ok(PreferencesPayload.of(preferences.execute()));
    }

    @Operation(
            summary = "Choose what interrupts me",
            description =
                    """
                    NOTIFICATION-PREFERENCES-01. Requires NOTIFICATION_VIEW_OWN.

                    MINE ALONE. No route lets anybody read or set another person's, including the owner.
                    If preferences were centrally configurable, disabling them would become a negotiation
                    with a manager — and a person unable to control their own interruptions mutes the
                    product at the operating system, where the escalations go too.

                    DISABLING A GROUP STOPS CREATION, NOT JUST DELIVERY. No row is written for a
                    suppressed notice, so there is nothing anywhere counting what somebody declined.

                    DISABLING NEVER HIDES A MARKER. At-risk, blocked and stalled are derived and render
                    on every screen regardless — a person who mutes loses the prompt, not the
                    information, and that is what makes muting a reasonable choice rather than a way to
                    hide from work.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Saved"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "422",
                description = "NOT_DISABLEABLE: a group that has no switch",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/notification-preferences")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW_OWN')")
    public ResponseEntity<Void> setPreferences(@Valid @RequestBody PreferencesPayload payload) {
        preferences.update(payload.asGroups());
        return ResponseEntity.noContent().build();
    }
}
