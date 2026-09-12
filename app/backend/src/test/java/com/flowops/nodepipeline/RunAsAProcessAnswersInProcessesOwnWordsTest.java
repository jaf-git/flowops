package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.api.PipelineProcessController;
import com.flowops.nodepipeline.api.exception.NodePipelineExceptionHandler;
import com.flowops.nodepipeline.application.port.ProcessRunPort;
import com.flowops.shared.published.PublishedRefusal;
import com.flowops.shared.published.RefusalKind;
import com.flowops.shared.web.ErrorResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class RunAsAProcessAnswersInProcessesOwnWordsTest {
    private static final UUID TEMPLATE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MARIA = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final NodePipelineExceptionHandler advice = new NodePipelineExceptionHandler();

    @Test
    void pressingTheButtonStartsARun() {
        UUID run = UUID.fromString("44444444-4444-4444-4444-444444444444");
        PipelineProcessController controller = new PipelineProcessController(new StubProcesses(run, null));

        ResponseEntity<PipelineProcessController.StartedRunResponse> answer =
                controller.start(TEMPLATE, new PipelineProcessController.StartRunRequest("Onboarding", MARIA));

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(answer.getBody()).isNotNull();
        assertThat(answer.getBody().instanceId()).isEqualTo(run);
    }

    @Test
    void aRetiredProcessIsRefusedInProcessesOwnWords() {
        ResponseEntity<ErrorResponse> answer = refusalOf(refusal(
                RefusalKind.CONFLICT,
                "TEMPLATE_IS_RETIRED",
                "This template has been retired. Runs already started from it are unaffected."));

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(answer.getBody()).isNotNull();
        assertThat(answer.getBody().code()).isEqualTo("TEMPLATE_IS_RETIRED");
        assertThat(answer.getBody().message()).startsWith("This template has been retired");
    }

    @Test
    void aProcessThatIsNotThereIsANotFound() {
        ResponseEntity<ErrorResponse> answer =
                refusalOf(refusal(RefusalKind.NOT_FOUND, "TEMPLATE_NOT_FOUND", "There is no such process."));

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(answer.getBody()).isNotNull();
        assertThat(answer.getBody().code()).isEqualTo("TEMPLATE_NOT_FOUND");
    }

    @Test
    void anInvalidGraphIsAConflictAndNoRunIsCreated() {
        assertThat(refusalOf(refusal(
                                RefusalKind.CONFLICT,
                                "GRAPH_CYCLE",
                                "Those steps would end up waiting for each other."))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(refusalOf(refusal(
                                RefusalKind.CONFLICT,
                                "STEP_STRANDED",
                                "That would leave a step nothing can ever reach."))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aDeactivatedProcessOwnerIsAConflictOnThisSideOfTheBoundary() {
        ResponseEntity<ErrorResponse> answer = refusalOf(refusal(
                RefusalKind.CONFLICT,
                "ASSIGNEE_NOT_ACTIVE",
                "That person is no longer active, so they cannot steer a run."));

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(answer.getBody()).isNotNull();
        assertThat(answer.getBody().code()).isEqualTo("ASSIGNEE_NOT_ACTIVE");
    }

    @Test
    void aRefusedCallerIsToldSoRatherThanShownNothing() {
        ResponseEntity<ErrorResponse> answer = refusalOf(refusal(
                RefusalKind.NOT_PERMITTED,
                "NOT_THE_AUTHOR",
                "That process is somebody else's to change. Ask whoever wrote it, or the owner."));

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "TemplateNotFoundException",
                "TemplateIsRetiredException",
                "ProcessOwnerNotActiveException",
                "CycleWouldFormException",
                "StepWouldBeStrandedException"
            })
    void everyFailureP16NamesIsTranslatedByProcess(String refusal) throws IOException {
        Path refusals = Path.of(
                "src", "main", "java", "com", "flowops", "process", "application", "published", "ProcessRefusals.java");
        assertThat(refusals).exists();
        assertThat(Files.readString(refusals)).contains("catch (" + refusal + " ");
    }

    private static PublishedRefusal refusal(RefusalKind kind, String code, String message) {
        return new PublishedRefusal(kind, code, message, null);
    }

    private ResponseEntity<ErrorResponse> refusalOf(PublishedRefusal refusal) {
        PipelineProcessController controller = new PipelineProcessController(new StubProcesses(null, refusal));
        try {
            controller.start(TEMPLATE, new PipelineProcessController.StartRunRequest(null, MARIA));
            throw new AssertionError("the stub was supposed to refuse");
        } catch (PublishedRefusal thrown) {
            return advice.onNeighbourRefusal(thrown);
        }
    }

    private record StubProcesses(UUID startedRun, PublishedRefusal refusal) implements ProcessRunPort {
        @Override
        public List<StartableProcess> startable() {
            if (refusal != null) {
                throw refusal;
            }
            return List.of();
        }

        @Override
        public UUID startRun(UUID templateId, String name, UUID processOwner) {
            if (refusal != null) {
                throw refusal;
            }
            return startedRun;
        }
    }
}
