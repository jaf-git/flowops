package com.flowops.chat.api;

import com.flowops.chat.api.dto.AssignTaskRequest;
import com.flowops.chat.api.dto.AssignmentContextResponse;
import com.flowops.chat.api.dto.ConversationsResponse;
import com.flowops.chat.api.dto.ConversionContextResponse;
import com.flowops.chat.api.dto.ConvertMessageRequest;
import com.flowops.chat.api.dto.MarkReadRequest;
import com.flowops.chat.api.dto.MessageSelectionRequests;
import com.flowops.chat.api.dto.MessagesResponse;
import com.flowops.chat.api.dto.SendMessageRequest;
import com.flowops.chat.api.dto.StartConversationRequest;
import com.flowops.chat.api.dto.StartRunRequest;
import com.flowops.chat.api.dto.WorkDraftResponse;
import com.flowops.chat.application.assignwork.AssignWorkInConversationUseCase;
import com.flowops.chat.application.buildprocess.BuildProcessFromMessagesUseCase;
import com.flowops.chat.application.buildprocess.ProcessDraft;
import com.flowops.chat.application.convertmessage.ConvertMessageToTaskUseCase;
import com.flowops.chat.application.managerooms.ManageRoomsUseCase;
import com.flowops.chat.application.sendmessage.SendMessageUseCase;
import com.flowops.chat.application.shared.port.MessageStorePort;
import com.flowops.chat.application.startconversation.StartConversationUseCase;
import com.flowops.chat.application.taskorigin.FindTaskOriginUseCase;
import com.flowops.chat.application.viewconversation.ViewConversationResult;
import com.flowops.chat.application.viewconversation.ViewConversationUseCase;
import com.flowops.chat.application.viewconversations.ViewConversationsResult;
import com.flowops.chat.application.viewconversations.ViewConversationsUseCase;
import com.flowops.chat.domain.model.Conversation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@Tag(name = "Chat", description = "Conversations, messages, and turning a message into a task.")
public class ChatController {
    private final ViewConversationsUseCase viewConversations;
    private final ViewConversationUseCase viewConversation;
    private final StartConversationUseCase startConversation;
    private final SendMessageUseCase sendMessage;
    private final ConvertMessageToTaskUseCase convertMessage;
    private final AssignWorkInConversationUseCase assignWork;
    private final BuildProcessFromMessagesUseCase buildProcess;
    private final FindTaskOriginUseCase taskOrigin;
    private final com.flowops.chat.application.managerooms.ManageRoomsUseCase manageRoomsUseCase;

    public ChatController(
            ViewConversationsUseCase viewConversations,
            ViewConversationUseCase viewConversation,
            StartConversationUseCase startConversation,
            SendMessageUseCase sendMessage,
            ConvertMessageToTaskUseCase convertMessage,
            AssignWorkInConversationUseCase assignWork,
            BuildProcessFromMessagesUseCase buildProcess,
            FindTaskOriginUseCase taskOrigin,
            com.flowops.chat.application.managerooms.ManageRoomsUseCase manageRoomsUseCase) {
        this.viewConversations = viewConversations;
        this.viewConversation = viewConversation;
        this.startConversation = startConversation;
        this.sendMessage = sendMessage;
        this.convertMessage = convertMessage;
        this.assignWork = assignWork;
        this.buildProcess = buildProcess;
        this.taskOrigin = taskOrigin;
        this.manageRoomsUseCase = manageRoomsUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    @Operation(
            summary = "My conversations, and what is unread",
            description =
                    """
                    CHAT-VIEW-CONVERSATIONS-01. Requires CHAT_PARTICIPATE, which every role holds.

                    **No person identifier is accepted**, and that is the security property rather than a
                    convenience: the list is the caller's, resolved from the session, so there is no
                    parameter naming whose rail to return and therefore no authorization decision to get
                    wrong.

                    Ordered by recency with Announcements pinned first, and **never by presence** — no
                    online section, no availability counter. The only number on the response is the
                    caller's own unread count; no route returns a message count, a response time or an
                    activity ordering for anybody.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The rail, and the stream cursor to subscribe from"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold CHAT_PARTICIPATE",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class)))
    })
    public ConversationsResponse conversations() {
        ViewConversationsResult result = viewConversations.execute();
        return new ConversationsResponse(
                result.conversations().stream()
                        .map(row -> new ConversationsResponse.Row(
                                row.id(),
                                row.kind().name(),
                                row.counterpartId().orElse(null),
                                row.counterpartName().orElse(null),
                                row.counterpartActive(),
                                row.name().orElse(null),
                                row.lastMessagePreview().orElse(null),
                                row.lastMessageDeleted(),
                                row.lastMessageAt().orElse(null),
                                row.unreadCount()))
                        .toList(),
                result.joinable().stream()
                        .map(room -> new ConversationsResponse.Room(room.id(), room.name()))
                        .toList(),
                result.cursor());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    @Operation(
            summary = "Start a conversation with somebody",
            description =
                    """
                    CHAT-VIEW-CONVERSATIONS-01, step 5. Requires CHAT_PARTICIPATE.

                    **Anybody may talk to anybody active**, regardless of reporting line, role or subtree
                    (CHAT_02 section 3) — a deliberate departure from every other read in the product,
                    because the tree describes who directs whose work and talking is not that. Nothing
                    follows from it: giving somebody work is still TASK-CREATE-01's rule, checked at
                    conversion.

                    **Idempotent.** Starting a conversation that already exists returns the existing one
                    rather than creating a second, guaranteed by a unique index on the normalised
                    participant pair — so two people opening it in the same instant get one conversation.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The conversation, existing or new"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "PERSON_NOT_FOUND: nobody here holds that identifier",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "COUNTERPART_NOT_ACTIVE: they have been deactivated or erased",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class)))
    })
    public ResponseEntity<ConversationsResponse.Row> start(@Valid @RequestBody StartConversationRequest request) {
        Conversation conversation = startConversation.execute(request.personId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ConversationsResponse.Row(
                        conversation.id().value(),
                        conversation.kind().name(),
                        request.personId(),
                        null,
                        true,
                        null,
                        null,
                        false,
                        null,
                        0L));
    }

    @Operation(
            summary = "Open a group anybody may join",
            description =
                    """
                    The one room a person creates by hand.

                    Open on purpose: anybody in the workspace can join and read it. A group whose
                    membership had to be curated would be a private room with extra steps, and directs
                    already do that — so the create form says plainly what it is rather than leaving
                    somebody to discover it.

                    It exists because work crosses crafts. A direct holds two people and a role channel
                    holds one kind of worker; one message assigning captions, photos, designs, video and
                    ads to five people has nowhere else to live.

                    The creator joins it, because a room its own author is not in is a room nobody is in.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Opened, with the creator in it."),
        @ApiResponse(responseCode = "400", description = "No name — a room somebody has to find again needs one.")
    })
    @PostMapping("/group")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    public ResponseEntity<ConversationsResponse.Row> startGroup(@Valid @RequestBody NameOnlyRequest request) {
        Conversation group = manageRoomsUseCase.startGroup(request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ConversationsResponse.Row(
                        group.id().value(),
                        group.kind().name(),
                        null,
                        null,
                        true,
                        group.name().orElse(null),
                        null,
                        false,
                        null,
                        0L));
    }

    @Operation(
            summary = "Join an open group",
            description =
                    """
                    Idempotent: pressing join twice says one thing twice, and refusing the second press
                    teaches somebody the control is unreliable.

                    Only a group. A role channel's membership IS the role assignment — joining one would
                    be claiming to hold a job, and the room would then disagree with the org chart about
                    who does what. The announcement channel is everybody already.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "In."),
        @ApiResponse(responseCode = "409", description = "That kind of room does not take members by choice.")
    })
    @PostMapping("/{id}/join")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    public ResponseEntity<Void> join(@PathVariable UUID id) {
        manageRoomsUseCase.join(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Leave a group", description = "Only a group. A channel is left by ceasing to hold the role.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Out."),
        @ApiResponse(responseCode = "409", description = "That kind of room does not take members by choice.")
    })
    @PostMapping("/{id}/leave")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    public ResponseEntity<Void> leave(@PathVariable UUID id) {
        manageRoomsUseCase.leave(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Rename a group", description = "A channel's name follows its role and is not renamed here.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Renamed."),
        @ApiResponse(responseCode = "409", description = "That room's name is not a person's to set.")
    })
    @PutMapping("/{id}/name")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    public ResponseEntity<Void> rename(@PathVariable UUID id, @Valid @RequestBody NameOnlyRequest request) {
        manageRoomsUseCase.rename(id, request.name());
        return ResponseEntity.noContent().build();
    }

    public record NameOnlyRequest(@jakarta.validation.constraints.NotBlank String name) {}

    @Operation(
            summary = "Who is in this room",
            description =
                    """
                    CHAT-MANAGE-ROOMS-01. Requires CHAT_PARTICIPATE **and participation in this
                    conversation**, resolved from the session — and a room the caller is not in answers
                    exactly as one that does not exist, for the reason every read here gives: a
                    distinguishable refusal, asked of enough identifiers, enumerates who talks to whom
                    (DECISION-CHAT-PRIVACY-01).

                    **This is the roster, and it is not a reporting line.** The observing zone's mark strip
                    was reading TASK's assignable-people list, which answers with a manager's subtree — so a
                    client manager with no reports was offered one person to name as a performer: himself,
                    and the messages he needed to mark for other people were unmarkable. The people you can
                    name for work you are discussing are the people in the room.

                    The announcement channel legitimately answers with nothing. It has no membership rows at
                    all, because it is everybody and a stored copy of the workspace roster would go stale the
                    moment somebody joined.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Everybody in the room."),
        @ApiResponse(responseCode = "404", description = "CONVERSATION_NOT_FOUND — or not one the caller is in.")
    })
    @GetMapping("/{id}/participants")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    public ResponseEntity<List<ManageRoomsUseCase.Participant>> participants(@PathVariable UUID id) {
        return ResponseEntity.ok(manageRoomsUseCase.participants(id));
    }

    @Operation(
            summary = "Bring somebody into a group",
            description =
                    """
                    CHAT-MANAGE-ROOMS-01. Requires CHAT_PARTICIPATE and participation in this conversation.

                    **The addition is announced with a line in the room.** A room whose membership changes
                    silently is a room where somebody's later messages are read by a person the earlier
                    speakers did not know was listening. Announced, never gated — the same treatment a new
                    client gets.

                    Idempotent on the membership and not on the line: pressing twice says one true thing
                    twice, so the second press adds nobody and announces nothing.

                    Only a group. A direct is two people by definition and the pairing is a check
                    constraint; a channel's membership IS the role assignment, so adding somebody there
                    would be claiming they hold a job the org chart says they do not. Somebody who has left
                    the workspace is refused rather than added quietly — they would appear in the roster the
                    mark strip reads, and work marked for them would open a bracket nobody can close.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "In, and the room was told."),
        @ApiResponse(responseCode = "404", description = "CONVERSATION_NOT_FOUND, or no such person."),
        @ApiResponse(responseCode = "409", description = "That kind of room does not take members by choice.")
    })
    @PostMapping("/{id}/participants")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    public ResponseEntity<Void> addParticipant(@PathVariable UUID id, @Valid @RequestBody PersonOnlyRequest request) {
        manageRoomsUseCase.addParticipant(id, request.personId());
        return ResponseEntity.noContent().build();
    }

    public record PersonOnlyRequest(@jakarta.validation.constraints.NotNull UUID personId) {}

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    @Operation(
            summary = "Read a conversation",
            description =
                    """
                    CHAT-VIEW-CONVERSATION-01. Requires CHAT_PARTICIPATE **and participation in this
                    conversation**, resolved from the session.

                    **A conversation the caller does not participate in answers exactly as one that does
                    not exist** — 404 CONVERSATION_NOT_FOUND for both, with the same message. If the two
                    differed, this route would be a way to ask which conversations exist and, over a few
                    probes, who is talking to whom (DECISION-CHAT-PRIVACY-01).

                    Reading appends nothing and notifies nobody. No receipt reaches the other
                    participant: a read receipt is a demand for a reply, and this product does not put
                    one in a colleague's hands.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "A page of the thread, newest first"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "CONVERSATION_NOT_FOUND: unknown and not-a-participant alike",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class)))
    })
    public MessagesResponse messages(
            @PathVariable UUID id,
            @RequestParam(name = "before", required = false) Long before,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        ViewConversationResult result = viewConversation.execute(id, Optional.ofNullable(before), limit);
        return new MessagesResponse(
                result.messages().stream()
                        .map(row -> new MessagesResponse.Row(
                                row.work().isPresent() ? "WORK_MARK" : "SPOKEN",
                                row.id(),
                                row.authorId(),
                                row.authorName(),
                                row.body().orElse(null),
                                row.sentAt(),
                                row.editedAt().orElse(null),
                                row.deletedAt().orElse(null),
                                row.convertedTaskId().orElse(null),
                                row.work()
                                        .map(work -> new MessagesResponse.Work(
                                                work.kind().name(), work.id()))
                                        .orElse(null),
                                row.seq()))
                        .toList(),
                result.cursor(),
                result.hasMore());
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    @Operation(
            summary = "Say something",
            description =
                    """
                    CHAT-SEND-MESSAGE-01. Requires CHAT_PARTICIPATE and participation in this
                    conversation.

                    One transaction: the message and its event. **The event carries no body** — the log
                    records that a message existed, by whom and when, never what it said, because an
                    event log is reachable by more paths than a conversation is (CHAT_03 section 8).

                    Sending records no delivery state, produces no receipt and sends no typing signal.
                    A message that has arrived simply is in the thread.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The message, with the sequence the stream will carry"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "CONVERSATION_NOT_FOUND: unknown and not-a-participant alike",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "MESSAGE_EMPTY, or MESSAGE_TOO_LONG with the limit in details",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class)))
    })
    public ResponseEntity<MessagesResponse.Row> send(@PathVariable UUID id, @RequestBody SendMessageRequest request) {
        MessageStorePort.Stored stored = sendMessage.execute(id, request.body());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessagesResponse.Row(
                        "SPOKEN",
                        stored.message().id().value(),
                        stored.message().author().value(),
                        null,
                        stored.message().body(),
                        stored.message().sentAt(),
                        null,
                        null,
                        null,
                        null,
                        stored.seq()));
    }

    @GetMapping("/origin/{taskId}")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    @Operation(
            summary = "Which message this task was made from",
            description =
                    """
                    CHAT-CONVERT-TO-TASK-01, the back-link half. Requires CHAT_PARTICIPATE and
                    participation in the conversation the message belongs to.

                    `CHAT_UC_04` promises the two participants a back-link from the task to what was
                    said, and promises everybody else that the same task reads as an ordinary task —
                    "not a dead link, not a permission stub, not a greyed reference". Both halves are
                    this one endpoint: participants get the pair of identifiers it takes to open the
                    message, and everybody else gets the same 404 as a task that came from no message
                    at all.

                    It widens nothing. The message it names is one the caller could already reach by
                    scrolling their own thread, and reading that thread still goes through
                    CHAT-VIEW-CONVERSATION-01 and its own participation check.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The conversation and message this task came from"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "CONVERSATION_NOT_FOUND: came from no message, and not-a-participant, alike",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class)))
    })
    public FindTaskOriginUseCase.Origin origin(@PathVariable UUID taskId) {
        return taskOrigin.execute(taskId);
    }

    @GetMapping("/{id}/messages/{messageId}/conversion-context")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    @Operation(
            summary = "What the create dialog opens pre-filled with",
            description =
                    """
                    CHAT-CONVERT-TO-TASK-01, steps 2–4. Requires CHAT_PARTICIPATE and participation.

                    The assignee is inferred from the conversation — the other participant — and the
                    text from the message. **No assignee is suggested for Announcements or a channel**:
                    a broadcast has no "other person" and a channel has many, so guessing one would hand
                    work to whoever happened to be listed first.

                    This is usability and never enforcement. It offers only what the caller could have
                    submitted anyway, and TASK's check on submit is still the rule.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The pre-fill"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "CONVERSATION_NOT_FOUND: unknown and not-a-participant alike",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class)))
    })
    public ConversionContextResponse conversionContext(@PathVariable UUID id, @PathVariable UUID messageId) {
        ConvertMessageToTaskUseCase.ConversionContext context = convertMessage.contextFor(id, messageId);
        return new ConversionContextResponse(
                context.suggestedAssignee().orElse(null),
                context.suggestedAssigneeName().orElse(null),
                context.suggestedAssigneeActive(),
                context.title(),
                context.description(),
                context.instances().stream()
                        .map(run -> new ConversionContextResponse.Run(run.id(), run.name()))
                        .toList());
    }

    @PostMapping("/{id}/messages/{messageId}/convert")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    @Operation(
            summary = "Turn this message into a task",
            description =
                    """
                    CHAT-CONVERT-TO-TASK-01 — the feature's reason for existing.

                    **CHAT judges nothing.** Whether the task may exist, for whom and with what
                    deadline is TASK's decision, made at TASK's own service. Every refusal it raises —
                    DEADLINE_IN_THE_PAST, ASSIGNEE_OUT_OF_SCOPE, ASSIGNEE_NOT_ACTIVE — passes through
                    with its own code and message unchanged, so a person converting a message meets
                    exactly the words they would have met on the task screen.

                    **The message text is copied into the task, never referenced.** Nobody outside the
                    conversation can read it (DECISION-CHAT-PRIVACY-01), so a task that merely pointed
                    at the message would be unreadable to the manager who has to act on it. To everyone
                    outside the conversation the result is an ordinary task with no chat trace — not a
                    dead link, not a permission stub.

                    **Idempotent per message.** A second conversion is refused and exactly one task
                    exists, guaranteed by a unique index rather than by the check — two simultaneous
                    submits both pass the check, and one of them loses in the database.

                    **Nothing is written on failure.** The downstream door is knocked on first; the
                    message records the task only once one exists, so a refused conversion leaves the
                    message untouched and still convertible.

                    **Two doors, exactly one call.** Without `instanceId` the work is an ordinary task
                    with no process, which DECISION-ADHOC-QUEUE-01 requires — no default process is
                    invented to hold it. With `instanceId` the whole thing goes through PROCESS's
                    create-and-attach shape instead, and PROCESS's refusals pass through in their turn.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The task that now exists"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "CONVERSATION_NOT_FOUND: unknown and not-a-participant alike",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "MESSAGE_ALREADY_CONVERTED, or MESSAGE_DELETED",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "TASK's own refusal, passed through unchanged",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, UUID>> convert(
            @PathVariable UUID id, @PathVariable UUID messageId, @Valid @RequestBody ConvertMessageRequest request) {
        UUID task = convertMessage.convert(
                id,
                messageId,
                new ConvertMessageToTaskUseCase.Draft(
                        request.title(),
                        request.description(),
                        request.assigneeId(),
                        request.deadline(),
                        request.priority(),
                        Optional.ofNullable(request.instanceId())));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("taskId", task));
    }

    @GetMapping("/{id}/assignment-context")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    @Operation(
            summary = "Who the other person is, and what work may be given to them",
            description =
                    """
                    CHAT-ASSIGN-DIRECT-01. Requires CHAT_PARTICIPATE and participation in this
                    conversation, resolved from the session.

                    Everything here decides whether a control **renders at all** — absent, never
                    disabled, because a greyed control advertises a door that is closed. The counterpart
                    is absent on the announcement channel and on a team channel: a broadcast has no
                    other person and a channel has many.

                    `mayAssignTask` is TASK's own answer over its own subtree rule, called rather than
                    copied. It is false outside the caller's subtree and false for somebody
                    deactivated, from the same read. `mayStartRun` is PROCESS's, over
                    `PROCESS_INSTANTIATE`, and is deliberately *not* subtree-scoped: a Process Owner is
                    a per-instance position rather than a role, so a run may be handed to somebody a
                    task may not be. The two controls therefore hide on different rules.

                    **`mayStartRun: false` and an empty `templates` are different answers.** The first
                    means this caller may not start runs at all and the control does not render; the
                    second means the library is empty, the control still renders, and its dialog says so
                    and offers the way to author one. Rendering both as an empty list is a defect this
                    endpoint shipped once and a fresh-eyes pass found.

                    This is usability and never enforcement. It offers only what the caller could have
                    submitted anyway, and TASK's and PROCESS's checks on submit are still the rule.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "What may be offered here"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "CONVERSATION_NOT_FOUND: unknown and not-a-participant alike",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class)))
    })
    public AssignmentContextResponse assignmentContext(@PathVariable UUID id) {
        AssignWorkInConversationUseCase.AssignmentContext context = assignWork.contextFor(id);
        return new AssignmentContextResponse(
                context.counterpart()
                        .map(AssignWorkInConversationUseCase.Counterpart::id)
                        .orElse(null),
                context.counterpart()
                        .map(AssignWorkInConversationUseCase.Counterpart::displayName)
                        .orElse(null),
                context.counterpart()
                        .map(AssignWorkInConversationUseCase.Counterpart::active)
                        .orElse(false),
                context.mayAssignTask(),
                context.mayStartRun(),
                context.templates().stream()
                        .map(template -> new AssignmentContextResponse.Template(
                                template.id(), template.name(), template.overview(), template.stepCount()))
                        .toList());
    }

    @PostMapping("/{id}/tasks")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    @Operation(
            summary = "Give the other person a task",
            description =
                    """
                    CHAT-ASSIGN-DIRECT-01. Requires CHAT_PARTICIPATE to reach and TASK_CREATE to
                    complete, enforced by TASK at its own service.

                    **The assignee is not a parameter.** It is the conversation's counterpart, resolved
                    server-side, so there is nothing to vary in order to reach somebody else — and CHAT
                    never grows a subtree check of its own, which would be a second implementation of
                    TASK's scope.

                    Exactly one downstream call is made, and it goes first. Only once a task exists is a
                    **work mark** appended to the thread — a row carrying who, what kind, and which
                    work, and **no sentence in any language**; each client assembles the words it shows.
                    If TASK refuses, nothing is appended and the conversation is unchanged: a mark
                    pointing at work that does not exist cannot occur.

                    Every refusal originating in TASK passes through with its own code and message
                    unchanged — DEADLINE_IN_THE_PAST, ASSIGNEE_OUT_OF_SCOPE, ASSIGNEE_NOT_ACTIVE are
                    not re-worded, re-coded or pre-empted here.

                    Two submits make two tasks. Unlike conversion, which is keyed to one sentence, there
                    is deliberately nothing idempotent about giving somebody two tasks in a row.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The task that now exists"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: no TASK_CREATE",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "CONVERSATION_NOT_FOUND: unknown and not-a-participant alike",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "CONVERSATION_NOT_DIRECT: an announcement or team channel has no one other person",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "TASK's own refusal, passed through unchanged",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, UUID>> assignTask(@PathVariable UUID id, @RequestBody AssignTaskRequest request) {
        UUID task = assignWork.assignTask(
                id,
                new AssignWorkInConversationUseCase.TaskDraft(
                        request.title(), request.description(), request.deadline(), request.priority()));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("taskId", task));
    }

    @PostMapping("/{id}/runs")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    @Operation(
            summary = "Start a process the other person will steer",
            description =
                    """
                    CHAT-ASSIGN-DIRECT-01. Requires CHAT_PARTICIPATE to reach and PROCESS_INSTANTIATE to
                    complete, enforced by PROCESS on its own side of the boundary.

                    **The counterpart becomes the run's Process Owner, and is not a parameter.** They
                    steer it and assign each step as it becomes reachable — they are *not* assigned its
                    work. No step is assigned to anybody by this endpoint.

                    The run takes the template's own name. A conversation has no name to lend it, and
                    the invented alternatives put a person's name on work in a library everybody reads.

                    As with the task path: one downstream call, and the work mark appended only after it
                    succeeds. GRAPH_CYCLE, GRAPH_STEP_STRANDED and TEMPLATE_IS_RETIRED — a template
                    edited or retired between render and submit — pass through verbatim.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The run that now exists"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: no PROCESS_INSTANTIATE",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "CONVERSATION_NOT_FOUND, or TEMPLATE_NOT_FOUND from PROCESS",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "CONVERSATION_NOT_DIRECT, or PROCESS's own TEMPLATE_IS_RETIRED and graph refusals",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, UUID>> startRun(@PathVariable UUID id, @RequestBody StartRunRequest request) {
        UUID instance = assignWork.startRun(id, request.templateId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("instanceId", instance));
    }

    @PostMapping("/{id}/message-selection/draft")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    @Operation(
            summary = "What would these messages become?",
            description =
                    """
                    CHAT-BUILD-PROCESS-FROM-MESSAGES-01. Requires CHAT_PARTICIPATE and participation in
                    this conversation, resolved from the session.

                    **It writes nothing.** What comes back lives in the browser until somebody submits
                    it - no run, no template change, no record that it was asked. POST because the
                    answer must not be replayed by a back button or a prefetch, not because anything
                    changes.

                    **No model is involved.** The person pointed at the messages, so every step title
                    is a slice of one of them by construction rather than by a grounding rule. This is
                    the door that exists for an installation with AI switched off, and it returns the
                    identical shape the model's own draft returns so that both open one form.

                    Every message identifier is checked against **this** conversation. One from a thread
                    the caller cannot read answers the same not-found an unknown conversation gives.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The draft, filled in far enough to review"),
        @ApiResponse(responseCode = "400", description = "REQUEST_INVALID: fewer than two, or more than fifty"),
        @ApiResponse(
                responseCode = "404",
                description = "CONVERSATION_NOT_FOUND: unknown, not-a-participant, or a message from elsewhere",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "MESSAGE_ALREADY_CONVERTED or MESSAGE_DELETED",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class)))
    })
    public WorkDraftResponse selectionDraft(
            @PathVariable UUID id, @Valid @RequestBody MessageSelectionRequests.DraftFromSelection request) {
        ProcessDraft draft = buildProcess.draftFrom(id, request.messageIds());
        return new WorkDraftResponse(
                "PROCESS",
                new WorkDraftResponse.Sourced(
                        draft.name().value(), draft.name().source().name()),
                new WorkDraftResponse.Sourced(
                        draft.processOwnerId().value().toString(),
                        draft.processOwnerId().source().name()),
                null,
                draft.steps().stream()
                        .map(step -> new WorkDraftResponse.Step(
                                step.messageId(),
                                new WorkDraftResponse.Sourced(
                                        step.title().value(),
                                        step.title().source().name()),
                                step.description(),
                                new WorkDraftResponse.Sourced(
                                        step.assigneeId().value().toString(),
                                        step.assigneeId().source().name()),
                                WorkDraftResponse.sourced(
                                        step.deadline().value(),
                                        step.deadline().source().name())))
                        .toList());
    }

    @PostMapping("/{id}/processes")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    @Operation(
            summary = "Turn the reviewed draft into a run, or into steps on a template",
            description =
                    """
                    CHAT-BUILD-PROCESS-FROM-MESSAGES-01. Requires CHAT_PARTICIPATE to reach and
                    PROCESS_INSTANTIATE or PROCESS_TEMPLATE_EDIT to complete, enforced by PROCESS.

                    **Two outcomes, named rather than toggled.** With `name`, a standalone run starts
                    now and each ticked message records the task it became. With `templateId`, the steps
                    are appended to the **end** of a template everybody will instantiate later, **no
                    dependency edge is drawn**, no run starts, and no message is marked with a task -
                    because no task was created. A template is a shape, so it carries no owner and no
                    deadline either.

                    **Nothing partial is ever written.** The whole submission is one transaction and the
                    downstream call goes first, so a step naming somebody the caller may not direct
                    takes the entire run down with it - no orphan tasks, no half-marked thread. A
                    message somebody converted while the form was open loses at conversion's own unique
                    index and takes the submission with it.

                    Every refusal from TASK or PROCESS passes through with its own code and message.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The run that now exists"),
        @ApiResponse(responseCode = "204", description = "The template gained the steps; no run started"),
        @ApiResponse(responseCode = "400", description = "REQUEST_INVALID: neither or both of name and templateId"),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or TASK's ASSIGNEE_OUT_OF_SCOPE for a step",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "CONVERSATION_NOT_FOUND, or TEMPLATE_NOT_FOUND from PROCESS",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "MESSAGE_ALREADY_CONVERTED, or PROCESS's own refusals",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, UUID>> buildProcess(
            @PathVariable UUID id, @Valid @RequestBody MessageSelectionRequests.BuildProcess request) {
        List<BuildProcessFromMessagesUseCase.SubmittedStep> steps = request.steps().stream()
                .map(step -> new BuildProcessFromMessagesUseCase.SubmittedStep(
                        step.messageId(), step.title(), step.description(), step.assigneeId(), step.deadline()))
                .toList();

        if (request.isStandaloneRun()) {
            UUID run =
                    buildProcess.startRun(id, new BuildProcessFromMessagesUseCase.RunSubmission(request.name(), steps));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("instanceId", run));
        }

        buildProcess.appendToTemplate(
                id, new BuildProcessFromMessagesUseCase.TemplateSubmission(request.templateId(), steps));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAuthority('CHAT_PARTICIPATE')")
    @Operation(
            summary = "Mark how far I have read",
            description =
                    """
                    CHAT-VIEW-CONVERSATION-01, step 5. The caller's own bookkeeping.

                    **The marker moves forward only.** Scrolling up through history does not un-read a
                    conversation, so the claim is compared against the stored marker's sequence rather
                    than trusted.

                    It appends no event and notifies nobody. An evented read marker would be the
                    read-receipt channel this use case refuses, arriving under another name.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "The marker is at least this far forward"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "CONVERSATION_NOT_FOUND: unknown and not-a-participant alike",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class)))
    })
    public ResponseEntity<Void> markRead(@PathVariable UUID id, @Valid @RequestBody MarkReadRequest request) {
        viewConversation.markRead(id, request.throughMessageId());
        return ResponseEntity.noContent().build();
    }
}
