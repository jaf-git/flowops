package com.flowops.aiexport.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("AI-EXPORT-DATASET-01")
class AiExportRoundTripTest extends CompanyScenarioTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void refusesAnEmptyWorkspaceWithAPlainMessageRatherThanEmptyFiles() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        ResponseEntity<String> refused = maria.post("/api/ai-export", "{}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NO_HISTORY");
    }

    @Test
    void isRefusedToEverybodyButTheOwner() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        aClosedTask(maria, company);

        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        assertThat(andrei.post("/api/ai-export", "{}").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(maria.post("/api/ai-export", "{}").getStatusCode())
                .as("and the owner, who holds it, gets through — which is what proves the grant landed")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void carriesOneFilePerRecordTypeItCanAnswerForAndSaysWhichThoseAre() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        aClosedTask(maria, company);

        Map<String, List<JsonNode>> archive = exportedBy(maria);

        assertThat(archive.keySet())
                .as("templates.jsonl is absent, not empty: TASKLIB is not built and an empty file would"
                        + " say the workspace has no templates")
                .containsExactlyInAnyOrder(
                        "header.jsonl", "tasks.jsonl", "instances.jsonl", "steps.jsonl", "conversions.jsonl");

        JsonNode header = archive.get("header.jsonl").get(0);
        assertThat(header.get("record_types"))
                .map(JsonNode::asText)
                .containsExactly("task", "instance", "step", "conversion");
        assertThat(header.get("sufficiency").has("templates_total"))
                .as("absent rather than zero — a zero is a measurement, and there is nothing to measure")
                .isFalse();
        assertThat(header.get("sufficiency").has("templates_with_fewer_than_5_uses"))
                .isFalse();
        assertThat(header.get("sufficiency")
                        .get("templates_with_no_completed_instance")
                        .isNumber())
                .as("this one reads ProcessTemplate, which is built, so it stands")
                .isTrue();
    }

    @Test
    void namesNobody() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        aClosedTask(maria, company);

        Map<String, List<JsonNode>> archive = exportedBy(maria);
        String everything = archive.toString();

        List<String> everybodysName =
                jdbc.queryForList("select display_name from auth_user where display_name is not null", String.class);
        List<String> everybodysAddress = jdbc.queryForList("select email from auth_user", String.class);

        assertThat(everybodysName)
                .as("the fixture must actually contain people, or this assertion passes over nothing")
                .isNotEmpty();

        for (String name : everybodysName) {
            assertThat(everything).as("the export names %s", name).doesNotContain(name);
        }
        for (String address : everybodysAddress) {
            assertThat(everything).as("the export carries %s", address).doesNotContain(address);
        }
        assertThat(everything).as("the export carries the mail domain").doesNotContain("atelier.ro");

        for (JsonNode task : archive.get("tasks.jsonl")) {
            assertThat(task.get("assignee_ref").asText()).matches("p_[A-Za-z0-9_-]{22}");
            assertThat(task.get("creator_ref").asText()).matches("p_[A-Za-z0-9_-]{22}");
        }
    }

    @Test
    void givesThatPersonAnUnrelatedTokenInTheNextExport() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        aClosedTask(maria, company);

        String monday =
                exportedBy(maria).get("tasks.jsonl").get(0).get("creator_ref").asText();
        String friday =
                exportedBy(maria).get("tasks.jsonl").get(0).get("creator_ref").asText();

        assertThat(monday).isNotEqualTo(friday);
    }

    @Test
    void carriesNoUnqualifiedDurationUnderAnyName() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        aClosedTask(maria, company);

        for (JsonNode task : exportedBy(maria).get("tasks.jsonl")) {
            JsonNode phases = task.get("phase_durations_ms");
            assertThat(fieldNamesOf(phases))
                    .as("exactly four keys and no fifth")
                    .containsExactlyInAnyOrder("work", "blocked", "waiting", "review");

            long sum = phases.get("work").asLong()
                    + phases.get("blocked").asLong()
                    + phases.get("waiting").asLong()
                    + phases.get("review").asLong();

            task.fields().forEachRemaining(field -> {
                if (field.getValue().isNumber() && sum > 0) {
                    assertThat(field.getValue().asLong())
                            .as(
                                    "%s carries the sum of the four phases, which is an unqualified duration"
                                            + " wearing another name",
                                    field.getKey())
                            .isNotEqualTo(sum);
                }
            });
        }
    }

    @Test
    void pairsNoFigureWithAPerson() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);
        aClosedTask(maria, company);

        for (JsonNode task : exportedBy(maria).get("tasks.jsonl")) {
            assertThat(fieldNamesOf(task.get("review")))
                    .as("the review outcome describes the work and names nobody")
                    .containsExactlyInAnyOrder("first_try_approved", "iterations");
        }
    }

    @Test
    void refusesAnUnknownSubjectWithTheSameShapeAsAnUnpermittedOne() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        ResponseEntity<String> unknown = maria.post(
                "/api/ai-export/scoped",
                "{\"subjectType\":\"process_template\",\"subjectId\":\"" + UUID.randomUUID() + "\"}");

        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(unknown.getBody()).get("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void treatsASubjectItCannotAnswerForAsNotFound() throws Exception {
        buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        ResponseEntity<String> unsupported = maria.post(
                "/api/ai-export/scoped",
                "{\"subjectType\":\"task_template\",\"subjectId\":\"" + UUID.randomUUID() + "\"}");

        assertThat(unsupported.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void isRefusedToEverybodyButTheOwnerOnTheScopedRouteToo() throws Exception {
        buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> refused = andrei.post(
                "/api/ai-export/scoped",
                "{\"subjectType\":\"process_template\",\"subjectId\":\"" + UUID.randomUUID() + "\"}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private Set<String> fieldNamesOf(JsonNode node) {
        Set<String> names = new java.util.LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private Map<String, List<JsonNode>> exportedBy(RoundTripClient caller) throws Exception {
        ResponseEntity<byte[]> response = caller.postForBytes("/api/ai-export", "{}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, List<JsonNode>> files = new LinkedHashMap<>();
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(response.getBody()))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                List<JsonNode> lines = new ArrayList<>();
                String body = new String(archive.readAllBytes(), StandardCharsets.UTF_8);
                for (String line : body.split("\n")) {
                    if (!line.isBlank()) {
                        lines.add(json.readTree(line));
                    }
                }
                files.put(entry.getName(), lines);
            }
        }
        return files;
    }

    private void aClosedTask(RoundTripClient maria, Company company) throws Exception {
        String body = "{\"title\":\"Call Sarah about invoices\",\"description\":null,\"assigneeId\":\""
                + company.andrei() + "\",\"deadline\":\"" + OffsetDateTime.now().plusDays(3)
                + "\",\"priority\":\"NORMAL\"}";
        ResponseEntity<String> created = maria.post("/api/tasks", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
