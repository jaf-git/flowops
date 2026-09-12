package com.flowops.aiexport.api;

import com.flowops.aiexport.application.exportdataset.ExportDatasetService;
import com.flowops.aiexport.application.port.AnalyticalReadPort;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequestMapping("/api/ai-export")
@Tag(name = "AI export", description = "The workspace's analytical history, as documented JSONL")
public class AiExportController {
    private final ExportDatasetService export;
    private final AnalyticalReadPort reads;

    public AiExportController(ExportDatasetService export, AnalyticalReadPort reads) {
        this.export = export;
        this.reads = reads;
    }

    public record ExportWindow(Instant from, Instant to) {}

    @Operation(
            summary = "Export the workspace's analytical history (AI-EXPORT-DATASET-01)",
            description = "Streams an archive of line-delimited JSON, one file per record type, projected"
                    + " in a single read-only snapshot. Every actor is a pseudonym freshly salted for this"
                    + " export, so the same person is unrecognisable in the next one. Requires AI_EXPORT_RUN,"
                    + " which is the owner's alone.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The archive, streamed"),
        @ApiResponse(
                responseCode = "401",
                description = "No session",
                content = @io.swagger.v3.oas.annotations.media.Content),
        @ApiResponse(responseCode = "403", description = "The caller does not hold AI_EXPORT_RUN"),
        @ApiResponse(responseCode = "409", description = "NO_HISTORY — the workspace has nothing to export yet")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('AI_EXPORT_RUN')")
    public ResponseEntity<InputStreamResource> exportDataset(@RequestBody(required = false) ExportWindow window) {
        Instant from = window == null ? null : window.from();
        Instant to = window == null ? null : window.to();

        Path archive = export.writeToTemporaryFile(new AnalyticalReadPort.Window(from, to, null));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"flowops-analytical-export.zip\"")
                .body(new InputStreamResource(deletingWhenClosed(archive)));
    }

    public record ScopedExportRequest(String subjectType, java.util.UUID subjectId) {}

    @Operation(
            summary = "Export one process template's history (AI-EXPORT-SCOPED-01)",
            description = "The same record shapes as a full export, filtered to one process template's"
                    + " runs, their steps and those steps' tasks — produced by the same code path, so a"
                    + " person checking a finding is reading the dataset the finding came from. Attached"
                    + " steps are included and are the point. The header carries a scope field."
                    + " Pseudonyms are salted per export, so a scoped file and a full one cannot be"
                    + " joined to re-identify anybody.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The archive, scoped"),
        @ApiResponse(responseCode = "403", description = "The caller does not hold AI_EXPORT_RUN"),
        @ApiResponse(
                responseCode = "404",
                description = "No such subject — byte for byte the answer an unpermitted caller gets"),
        @ApiResponse(responseCode = "409", description = "NO_HISTORY — the subject has nothing to export")
    })
    @PostMapping("/scoped")
    @PreAuthorize("hasAuthority('AI_EXPORT_RUN')")
    public ResponseEntity<InputStreamResource> exportScoped(@RequestBody ScopedExportRequest request) {
        if (!"process_template".equals(request.subjectType()) || request.subjectId() == null) {
            throw new SubjectNotFoundException();
        }
        if (!reads.processTemplateExists(request.subjectId())) {
            throw new SubjectNotFoundException();
        }

        Path archive = export.writeToTemporaryFile(new AnalyticalReadPort.Window(null, null, request.subjectId()));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"flowops-scoped-export.zip\"")
                .body(new InputStreamResource(deletingWhenClosed(archive)));
    }

    static class SubjectNotFoundException extends RuntimeException {}

    private static InputStream deletingWhenClosed(Path file) {
        try {
            InputStream reading = Files.newInputStream(file);
            return new java.io.FilterInputStream(reading) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        Files.deleteIfExists(file);
                    }
                }
            };
        } catch (IOException unreadable) {
            throw new UncheckedIOException("the export was written and could not be read back", unreadable);
        }
    }

    @RestControllerAdvice(basePackages = "com.flowops.aiexport.api")
    static class AiExportExceptionHandler {
        @ExceptionHandler(ExportDatasetService.NoHistoryException.class)
        ResponseEntity<ErrorResponse> onNothingToExport(ExportDatasetService.NoHistoryException refusal) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of("NO_HISTORY", "There is no history to export yet.", List.of()));
        }

        @ExceptionHandler(SubjectNotFoundException.class)
        ResponseEntity<ErrorResponse> onUnknownSubject(SubjectNotFoundException refusal) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of("NOT_FOUND", "No such subject.", List.of()));
        }

        @ExceptionHandler(AuthorizationDeniedException.class)
        ResponseEntity<ErrorResponse> onDenied(AuthorizationDeniedException refusal) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorResponse.of("NOT_PERMITTED", "You do not have permission to do that.", List.of()));
        }
    }
}
