package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("PROCESS-VIEW-INSTANCE-01")
class NoPerPersonAggregateExistsTest {
    private static final Path PROCESS_SOURCES = Path.of("src/main/java/com/flowops/process");

    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("group\\s+by", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bavg\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("count\\s*\\([^)]*(assignee|owner|person|user)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("sum\\s*\\([^)]*(assignee|owner|person|user)", Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "throughput|leaderboard|ranking|perPerson|byAssignee|perAssignee", Pattern.CASE_INSENSITIVE));

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    @Test
    void nothingInProcessGroupsMeasurementByPerson() throws IOException {
        List<String> offences = new ArrayList<>();
        int scanned = 0;

        try (Stream<Path> sources = Files.walk(PROCESS_SOURCES)) {
            List<Path> files =
                    sources.filter(path -> path.toString().endsWith(".java")).toList();
            assertThat(files)
                    .as("no PROCESS sources were read; this scan would pass over an empty set and "
                            + "report a guarantee it never checked")
                    .isNotEmpty();

            for (Path file : files) {
                scanned++;
                String code = withoutComments(Files.readString(file, StandardCharsets.UTF_8));
                for (Pattern forbidden : FORBIDDEN) {
                    if (forbidden.matcher(code).find()) {
                        offences.add("%s matches %s".formatted(file.getFileName(), forbidden.pattern()));
                    }
                }
            }
        }

        assertThat(scanned)
                .as("the population is asserted before the result is believed")
                .isGreaterThan(30);

        assertThat(offences)
                .as(
                        """
                        PROCESS must present no per-person aggregate of any kind, and the guarantee is the \
                        absence of the query rather than a filter over its result \
                        (DECISION-PROCESS-BOTTLENECK-01). "Step 3 has waited four days across six instances" \
                        is a finding a business can act on; "Andrei is slow" is not, and is usually false, \
                        because the wait was queue time in front of him rather than work by him. If one of \
                        these is a legitimate query, it needs an owner ruling and not a wider pattern.\
                        """)
                .isEmpty();
    }

    private static final Map<String, Set<String>> PERMITTED = Map.ofEntries(
            Map.entry(
                    "InstanceResponse",
                    Set.of(
                            "id",
                            "name",
                            "state",
                            "templateId",
                            "templateName",
                            "processOwnerId",
                            "startedAt",
                            "completedAt",
                            "progress",
                            "steps",
                            "edges",
                            "awaitingAssignment",
                            "bottleneck",
                            "totalDurationMinutes",
                            "abandonedAt",
                            "abandonedReason",
                            "closureNote",
                            "needingAttention")),
            Map.entry(
                    "InstanceStepResponse",
                    Set.of(
                            "id",
                            "definitionId",
                            "title",
                            "description",
                            "expectedDurationHours",
                            "position",
                            "condition",
                            "taskId",
                            "planned",
                            "dependsOn",
                            "taskState",
                            "assigneeId",
                            "assigneeName",
                            "deadline",
                            "atRisk",
                            "phases",
                            "blockedReason",
                            "optional",
                            "conditionNote",
                            "skipped")),
            Map.entry("ProcessMetadataRequest", Set.of("triggerNote", "endCondition", "ownerRole")),
            Map.entry("ProcessMetadataResponse", Set.of("triggerNote", "endCondition", "ownerRole")),
            Map.entry(
                    "AddTaskRequest",
                    Set.of("taskId", "title", "description", "assigneeId", "deadline", "priority", "dependsOnStepIds")),
            Map.entry("AttachableTasksResponse", Set.of("tasks")),
            Map.entry("AttachableTasksResponse.Row", Set.of("id", "title", "state", "assigneeId", "deadline")),
            Map.entry("ReorderTasksRequest", Set.of("stepIds")),
            Map.entry("StartFromTasksRequest", Set.of("name", "processOwnerId", "taskIds")),
            Map.entry("StartFromDescriptionsRequest", Set.of("name", "processOwnerId", "steps")),
            Map.entry("Step", Set.of("title", "description", "assigneeId", "deadline", "priority")),
            Map.entry("InstanceEdgeResponse", Set.of("from", "to", "satisfied")),
            Map.entry("StepPhaseResponse", Set.of("kind", "seconds")),
            Map.entry("ProgressResponse", Set.of("closed", "total")),
            Map.entry("BottleneckResponse", Set.of("stepId", "waitedMinutes")),
            Map.entry(
                    "InstanceSummaryResponse",
                    Set.of("id", "name", "templateName", "state", "progress", "awaitingAssignmentCount")),
            Map.entry("InstanceListResponse", Set.of("instances")),
            Map.entry(
                    "TemplateResponse",
                    Set.of(
                            "id",
                            "name",
                            "overview",
                            "active",
                            "authorId",
                            "createdAt",
                            "steps",
                            "dependencies",
                            "metadata")),
            Map.entry("TemplateSummaryResponse", Set.of("id", "name", "overview", "stepCount", "active")),
            Map.entry("TemplateListResponse", Set.of("templates")),
            Map.entry(
                    "StepResponse",
                    Set.of(
                            "id",
                            "taskTemplateId",
                            "title",
                            "description",
                            "expectedDurationHours",
                            "position",
                            "optional",
                            "conditionNote")),
            Map.entry("DependencyResponse", Set.of("dependentStepId", "dependsOnStepId")),
            Map.entry("TemplateUsesResponse", Set.of("processTemplates", "runs", "runsTotal")),
            Map.entry("PlannedInResponse", Set.of("templateId", "name", "position", "active")),
            Map.entry(
                    "RunResponse",
                    Set.of(
                            "instanceId",
                            "instanceName",
                            "instanceState",
                            "stepId",
                            "stepCondition",
                            "taskId",
                            "startedAt")),
            Map.entry("AssignablePeopleResponse", Set.of("people")),
            Map.entry("AssignStepRequest", Set.of("assigneeId", "deadline")),
            Map.entry("InstantiateRequest", Set.of("templateId", "name", "processOwnerId")),
            Map.entry("AuthorTemplateRequest", Set.of("name", "overview", "steps")),
            Map.entry("EditTemplateRequest", Set.of("name", "overview", "steps")),
            Map.entry(
                    "StepRequest",
                    Set.of("id", "taskTemplateId", "expectedDurationHours", "optional", "conditionNote")),
            Map.entry("DependencyRequest", Set.of("dependentStepId", "dependsOnStepId")),
            Map.entry("AbandonInstanceRequest", Set.of("reason")),
            Map.entry("CloseInstanceRequest", Set.of("note")));

    @Test
    void everyFieldOnTheWireSurfaceIsOneSomebodyDecidedToPutThere() throws Exception {
        Path dtos = Path.of("src/main/java/com/flowops/process/api/dto");
        List<String> unexpected = new ArrayList<>();
        int checked = 0;

        try (Stream<Path> files = Files.list(dtos)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String simple = file.getFileName().toString().replace(".java", "");
                Class<?> dto = Class.forName("com.flowops.process.api.dto." + simple);

                Set<String> permitted = PERMITTED.get(simple);
                if (permitted == null) {
                    unexpected.add(simple + " is a response shape nobody has listed here");
                    continue;
                }

                checked++;
                for (RecordComponent component : dto.getRecordComponents()) {
                    if (!permitted.contains(component.getName())) {
                        unexpected.add(simple + "." + component.getName());
                    }
                }
            }
        }

        assertThat(checked)
                .as("the population is asserted before the result is believed")
                .isGreaterThan(15);

        assertThat(unexpected)
                .as(
                        """
                        A field arrived on PROCESS's wire surface that nobody listed.

                        If it is a fact about the work -- a step, an instance, a dependency --
                        add it to PERMITTED and it is allowed. If it is a measurement keyed to
                        whoever holds the work, it is the artefact DECISION-PROCESS-BOTTLENECK-01
                        forbids, and the answer is not to widen the list.

                        This test exists because the forbid-list above let assigneeMinutesSpent
                        through: a per-person duration needs no group by, no avg( and no route.
                        """)
                .isEmpty();
    }

    private static String withoutComments(String source) {
        return LINE_COMMENT
                .matcher(BLOCK_COMMENT.matcher(source).replaceAll(" "))
                .replaceAll(" ")
                .toLowerCase(Locale.ROOT);
    }
}
