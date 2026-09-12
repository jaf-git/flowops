package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("TASK-CATEGORISE-TASKS-01")
class TaskCategoryRoundTripTest extends TaskScenarioTest {
    private static final String CATEGORIES = "/api/task-categories";
    private static final String AURORA = "Aurora Coffee";

    @Test
    void mariaNamesAGroupingFilesTwoTasksAndBothTheCountAndTheRowsNarrowToIt() throws Exception {
        Company company = buildTheCompany();
        String supplierReview = taskFor(company.andrei());
        String ledger = taskFor(company.andrei());
        String unrelated = taskFor(company.elena());

        String aurora = nameAGrouping(browser, AURORA);
        assertThat(file(browser, supplierReview, aurora).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(file(browser, ledger, aurora).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode counted = json.readTree(
                browser.get("/api/tasks/sections?category=" + aurora).getBody());
        assertThat(counted.get("total").asInt())
                .as("two of the three are filed under it")
                .isEqualTo(2);

        JsonNode paged = json.readTree(
                browser.get("/api/tasks?section=not-started&category=" + aurora).getBody());
        assertThat(paged.get("rows")).hasSize(2);
        assertThat(paged.get("total"))
                .as("the pager's total describes the narrowed section, not the whole queue")
                .isNotNull();
        assertThat(paged.get("total").asInt()).isEqualTo(counted.get("total").asInt());

        for (JsonNode row : paged.get("rows")) {
            assertThat(row.get("categoryId").asText()).isEqualTo(aurora);
            assertThat(row.get("categoryName").asText()).isEqualTo(AURORA);
            assertThat(row.get("id").asText()).isNotEqualTo(unrelated);
        }

        JsonNode groupings = json.readTree(browser.get(CATEGORIES).getBody());
        assertThat(groupings.get("categories")).hasSize(1);
        assertThat(groupings.get("categories").get(0).get("taskCount").asInt())
                .as("a count keyed to the grouping, which is what CANVAS_00 section 5 permits")
                .isEqualTo(2);
        assertThat(groupings.get("filings").get(supplierReview).asText()).isEqualTo(aurora);
        assertThat(groupings.get("filings").has(unrelated))
                .as("an unfiled task is absent rather than present with a null")
                .isFalse();
    }

    @Test
    void aSecondGroupingOfTheSameNameIgnoringCaseAndSpaceIsRefusedAsAConflict() throws Exception {
        buildTheCompany();
        nameAGrouping(browser, AURORA);

        ResponseEntity<String> refused = send(browser, HttpMethod.POST, CATEGORIES, Map.of("name", "  aurora COFFEE "));

        assertThat(refused.getStatusCode())
                .as("a conflict, not a 500 — the server did not fail, it declined")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("CATEGORY_NAME_TAKEN");
        assertThat(jdbc.queryForObject("select count(*) from task_category", Integer.class))
                .as("refused means nothing was written")
                .isEqualTo(1);
    }

    @Test
    void removingAGroupingLeavesItsTasksAndEmptiesTheirFiling() throws Exception {
        Company company = buildTheCompany();
        String supplierReview = taskFor(company.andrei());
        String aurora = nameAGrouping(browser, AURORA);
        file(browser, supplierReview, aurora);

        assertThat(browser.delete(CATEGORIES + "/" + aurora).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(jdbc.queryForObject("select count(*) from task where id = ?::uuid", Integer.class, supplierReview))
                .as("the work is still there")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select category_id from task where id = ?::uuid", UUID.class, supplierReview))
                .as("and it is uncategorised, which is where it started")
                .isNull();
        assertThat(json.readTree(browser.get(CATEGORIES).getBody()).get("categories"))
                .isEmpty();
    }

    @Test
    void namingAGroupingIsTheOwnersAloneWhileFilingIsAnOrdinaryEdit() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        String hisTask = json.readTree(ionut.post("/api/tasks", body(company.andrei(), farOff()))
                        .getBody())
                .get("id")
                .asText();

        ResponseEntity<String> refused = send(ionut, HttpMethod.POST, CATEGORIES, Map.of("name", "Admin"));
        assertThat(refused.getStatusCode())
                .as("a manager does not invent vocabulary the whole workspace reads")
                .isEqualTo(HttpStatus.FORBIDDEN);

        String aurora = nameAGrouping(browser, AURORA);
        assertThat(file(ionut, hisTask, aurora).getStatusCode())
                .as("but filing work he gave out is an edit of that work")
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void aGroupingFromNowhereIsRefusedForFilingAndNarrowsToNothingForReading() throws Exception {
        Company company = buildTheCompany();
        String supplierReview = taskFor(company.andrei());
        String aurora = nameAGrouping(browser, AURORA);
        file(browser, supplierReview, aurora);
        UUID nowhere = UUID.randomUUID();

        ResponseEntity<String> refused = file(browser, supplierReview, nowhere.toString());
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("CATEGORY_NOT_FOUND");
        assertThat(jdbc.queryForObject("select category_id from task where id = ?::uuid", UUID.class, supplierReview))
                .as("a refused filing left the task in the grouping it was already in")
                .isEqualTo(UUID.fromString(aurora));

        ResponseEntity<String> narrowed = browser.get("/api/tasks/sections?category=" + nowhere);
        assertThat(narrowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(narrowed.getBody()).get("total").asInt()).isZero();
    }

    @Test
    void filingUnderNothingTakesATaskOutOfItsGrouping() throws Exception {
        Company company = buildTheCompany();
        String supplierReview = taskFor(company.andrei());
        String aurora = nameAGrouping(browser, AURORA);
        file(browser, supplierReview, aurora);

        assertThat(send(
                                browser,
                                HttpMethod.PUT,
                                CATEGORIES + "/tasks/" + supplierReview,
                                java.util.Collections.singletonMap("categoryId", null))
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(jdbc.queryForObject("select category_id from task where id = ?::uuid", UUID.class, supplierReview))
                .isNull();
    }

    private String taskFor(UUID assignee) throws Exception {
        ResponseEntity<String> created = browser.post("/api/tasks", body(assignee, farOff()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(created.getBody()).get("id").asText();
    }

    private static Instant farOff() {
        return Instant.now().plusSeconds(60L * 86_400);
    }

    private String nameAGrouping(RoundTripClient who, String name) throws Exception {
        ResponseEntity<String> created = send(who, HttpMethod.POST, CATEGORIES, Map.of("name", name));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(created.getBody()).get("id").asText();
    }

    private ResponseEntity<String> file(RoundTripClient who, String task, String category) throws Exception {
        return send(who, HttpMethod.PUT, CATEGORIES + "/tasks/" + task, Map.of("categoryId", category));
    }

    private ResponseEntity<String> send(RoundTripClient client, HttpMethod method, String path, Map<String, ?> body)
            throws Exception {
        return client.exchange(method, path, json.writeValueAsString(body));
    }
}
