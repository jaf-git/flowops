package com.flowops.canvas.api;

import com.flowops.canvas.application.shared.port.CanvasCallerPort;
import com.flowops.canvas.application.stream.CanvasDelta;
import com.flowops.canvas.application.stream.CanvasSink;
import com.flowops.canvas.application.stream.StreamNotAvailableException;
import com.flowops.canvas.application.stream.SubscribeToInstanceUseCase;
import com.flowops.canvas.application.stream.exception.CursorTooOldException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/canvas")
@Tag(name = "Canvas stream", description = "Live deltas for a canvas, over server-sent events.")
public class CanvasStreamController {
    private static final long HOLD_FOR = Duration.ofMinutes(30).toMillis();

    private static final Logger LOG = LoggerFactory.getLogger(CanvasStreamController.class);

    private final SubscribeToInstanceUseCase subscribe;
    private final CanvasCallerPort caller;

    public CanvasStreamController(SubscribeToInstanceUseCase subscribe, CanvasCallerPort caller) {
        this.subscribe = subscribe;
        this.caller = caller;
    }

    @GetMapping(value = "/stream/process-instances/{instanceId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('PROCESS_VIEW_OWN')")
    @Operation(
            summary = "Watch one running process, live",
            description =
                    """
                    CANVAS-VIEW-PROCESS-01, the live half. Requires PROCESS_VIEW_OWN at this endpoint, and
                    PROCESS's own scope rule is asked again **for every event emitted** rather than once at
                    connect (CANVAS_02 section 4) — a stream authorised once and trusted forever is a cache
                    of somebody's old position in the reporting tree.

                    Each event carries an identifier and a kind and **never a payload of anybody's data**;
                    the client refetches what it may then read. Every event's SSE `id` is its cursor, so the
                    browser's own reconnect resumes from it.

                    Passing `cursor` replays what was missed. A gap larger than the replay bound answers
                    with a `stale` event carrying `CURSOR_TOO_OLD` and closes, and the client refetches the
                    snapshot instead — correctness over cleverness. The event is named `stale` rather than
                    `error` because `EventSource` dispatches its own transport failures under that name,
                    and a client cannot be asked to tell a dropped connection from a refused cursor by
                    inspecting whether the event happens to carry data.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The stream, until it is closed by either end"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold PROCESS_VIEW_OWN",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "STREAM_NOT_AVAILABLE: absent and out-of-scope answer alike",
                content = @Content(schema = @Schema(implementation = com.flowops.shared.web.ErrorResponse.class)))
    })
    public SseEmitter streamOfInstance(
            @PathVariable UUID instanceId,
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestHeader(name = "Last-Event-ID", required = false) Long lastEventId) {
        UUID person = caller.currentCaller().orElseThrow(StreamNotAvailableException::new);

        SseEmitter emitter = new SseEmitter(HOLD_FOR);

        Long resumeFrom = lastEventId != null ? lastEventId : cursor;

        UUID subscription;
        try {
            subscription = subscribe.subscribe(
                    person, caller.callerPermissions(), instanceId, resumeFrom, sinkWriting(emitter));
        } catch (CursorTooOldException tooOld) {
            sendRefusalAndClose(emitter, "CURSOR_TOO_OLD");
            return emitter;
        }

        emitter.onCompletion(() -> subscribe.unsubscribe(subscription));
        emitter.onTimeout(() -> subscribe.unsubscribe(subscription));
        emitter.onError(failure -> subscribe.unsubscribe(subscription));

        greet(emitter);
        return emitter;
    }

    private void greet(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("subscribed"));
        } catch (IOException writing) {
            LOG.debug("a stream closed before it could be greeted", writing);
        }
    }

    @GetMapping("/cursor")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Where the stream stands, for a client about to read a snapshot",
            description =
                    """
                    CANVAS-VIEW-PROCESS-01, the live half. `DECISION-REALTIME-PROTOCOL-01` is
                    snapshot-then-delta, and the two must not leave a gap between them: a client reads this
                    **before** its snapshot and subscribes from it afterwards, so anything that happens
                    during the snapshot's own read is replayed rather than missed. The error is on the side
                    of delivering a delta twice, which the protocol makes harmless.

                    A session and no permission. The answer is one number and carries no identifier, no
                    instance and nothing about anybody — knowing that *something* moved somewhere is not
                    knowing what, or whose.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The cursor as it stands now; zero when nothing has happened"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content)
    })
    public Map<String, Long> currentCursor() {
        return Map.of("cursor", subscribe.currentCursor());
    }

    private CanvasSink sinkWriting(SseEmitter emitter) {
        return new CanvasSink() {
            @Override
            public void send(CanvasDelta delta) throws IOException {
                emitter.send(SseEmitter.event()
                        .id(Long.toString(delta.cursor()))
                        .name("delta")
                        .data(Map.of("kind", delta.kind(), "task", delta.task().toString())));
            }

            @Override
            public void keepAlive() throws IOException {
                emitter.send(SseEmitter.event().name("beat").data(""));
            }

            @Override
            public void close() {
                try {
                    emitter.complete();
                } catch (Exception alreadyGone) {
                    LOG.debug("a stream could not be completed; it had already ended", alreadyGone);
                }
            }
        };
    }

    private void sendRefusalAndClose(SseEmitter emitter, String code) {
        try {
            emitter.send(SseEmitter.event().name("stale").data(Map.of("code", code)));
        } catch (IOException writing) {
            LOG.debug("a stream closed before its refusal could be written", writing);
        }
        emitter.complete();
    }
}
