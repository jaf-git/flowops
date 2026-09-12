package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.job.JobMatcher;
import com.flowops.nodepipeline.domain.job.JobVerdict;
import com.flowops.nodepipeline.domain.job.JobWeights;
import com.flowops.nodepipeline.domain.job.PipelineJob;
import com.flowops.nodepipeline.domain.job.ProcessShape;
import com.flowops.nodepipeline.domain.match.NodeMatcher;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class JobMatcherMatchesTheOracleTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final LocalDate TODAY = LocalDate.parse("2026-07-20");

    @Test
    void every18BrutalJobsLandsOnTheTierTheOracleReached() throws Exception {
        Fixture fixture = load();
        JobMatcher matcher = new JobMatcher(
                new NodeMatcher(fixture.nodes.weights(), fixture.nodes.lexicons()), JobWeights.reference(), TODAY);

        List<String> disagreements = new ArrayList<>();

        for (PipelineJob job : fixture.jobs) {
            Expected expected = fixture.expected.get(job.id());
            assertThat(expected)
                    .as("the fixture must carry a verdict for job %s", job.id())
                    .isNotNull();

            List<PipelineNode> jobNodes = fixture.nodesByJob.getOrDefault(job.id(), List.of());
            JobVerdict actual =
                    matcher.match(job, jobNodes, fixture.nodes.templates(), fixture.processes, job.standing());

            if (!expected.tier.equals(actual.tier().name())) {
                disagreements.add("%s tier: oracle %s (%s), java %s (%s)"
                        .formatted(job.id(), expected.tier, expected.why, actual.tier(), actual.why()));
                continue;
            }
            if (!expected.why.equals(actual.why())) {
                disagreements.add("%s why: oracle '%s', java '%s'".formatted(job.id(), expected.why, actual.why()));
            }
        }

        assertThat(disagreements)
                .as("%d jobs, and Java must reach the oracle's tier and reason on each", fixture.jobs.size())
                .isEmpty();
    }

    @Test
    void theShapeOfTheAnswerIsThirteenDiscoveryCandidates() throws Exception {
        Fixture fixture = load();
        JobMatcher matcher = new JobMatcher(
                new NodeMatcher(fixture.nodes.weights(), fixture.nodes.lexicons()), JobWeights.reference(), TODAY);

        Map<String, Integer> tiers = new TreeMap<>();
        for (PipelineJob job : fixture.jobs) {
            JobVerdict verdict = matcher.match(
                    job,
                    fixture.nodesByJob.getOrDefault(job.id(), List.of()),
                    fixture.nodes.templates(),
                    fixture.processes,
                    job.standing());
            tiers.merge(verdict.tier().name(), 1, Integer::sum);
        }

        assertThat(tiers)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "UNKNOWN_PATTERN", 13,
                        "NOT_SHAPE_EVIDENCE", 1,
                        "REWORK", 1,
                        "STALE", 2,
                        "IN_PROGRESS", 1));
    }

    @Test
    void anEmptyProcessListIsAnAnswerRatherThanAnError() throws Exception {
        Fixture fixture = load();

        assertThat(fixture.processes)
                .as("the brutal corpus deliberately has none")
                .isEmpty();
        assertThat(fixture.jobs.size())
                .as("P5's volume guard is 50 jobs per run")
                .isLessThanOrEqualTo(50);

        JobMatcher matcher = new JobMatcher(
                new NodeMatcher(fixture.nodes.weights(), fixture.nodes.lexicons()), JobWeights.reference(), TODAY);

        for (PipelineJob job : fixture.jobs) {
            JobVerdict verdict = matcher.match(
                    job,
                    fixture.nodesByJob.getOrDefault(job.id(), List.of()),
                    fixture.nodes.templates(),
                    List.of(),
                    job.standing());
            assertThat(verdict.tier()).isNotNull();
            assertThat(verdict.processId())
                    .as("nothing was matched, so nothing is claimed")
                    .isNull();
        }
    }

    @Test
    void scopeIsReadFromTheCounterpartyEvenWhereTheOracleHardCodedIt() throws Exception {
        Fixture fixture = load();
        JobMatcher matcher = new JobMatcher(
                new NodeMatcher(fixture.nodes.weights(), fixture.nodes.lexicons()), JobWeights.reference(), TODAY);

        Map<String, String> scopes = new LinkedHashMap<>();
        for (PipelineJob job : fixture.jobs) {
            scopes.put(
                    job.id(),
                    matcher.match(
                                    job,
                                    fixture.nodesByJob.getOrDefault(job.id(), List.of()),
                                    fixture.nodes.templates(),
                                    fixture.processes,
                                    job.standing())
                            .scope());
        }

        assertThat(scopes.get("G07")).isEqualTo("PROSPECT");
        assertThat(scopes.get("G11")).isEqualTo("INTERNAL");
        assertThat(scopes.get("G13")).isEqualTo("PROSPECT");
        assertThat(scopes.get("G15")).isEqualTo("SUPPLIER");

        assertThat(scopes.values())
                .as("and everything else is a client, which is what the oracle said for all of them")
                .contains("CLIENT");
    }

    @Test
    void aFinishedJobThatMatchesNothingIsADiscoveryCandidate() throws Exception {
        Fixture fixture = load();
        JobMatcher matcher = new JobMatcher(
                new NodeMatcher(fixture.nodes.weights(), fixture.nodes.lexicons()), JobWeights.reference(), TODAY);

        long candidates = fixture.jobs.stream()
                .map(job -> matcher.match(
                        job,
                        fixture.nodesByJob.getOrDefault(job.id(), List.of()),
                        fixture.nodes.templates(),
                        fixture.processes,
                        job.standing()))
                .filter(JobVerdict::isDiscoveryCandidate)
                .count();

        assertThat(candidates)
                .as("a workspace whose every job answers this is not broken -- it is one whose processes "
                        + "have never been written down, which is the ordinary case in month one")
                .isEqualTo(13);
    }

    private record Expected(String tier, String why, String scope) {}

    private record Fixture(
            GoldenCorpus nodes,
            List<PipelineJob> jobs,
            List<ProcessShape> processes,
            Map<String, List<PipelineNode>> nodesByJob,
            Map<String, Expected> expected) {}

    private static Fixture load() throws Exception {
        GoldenCorpus nodes = GoldenCorpus.load("brutal");

        try (InputStream stream =
                JobMatcherMatchesTheOracleTest.class.getResourceAsStream("/nodepipeline/golden/brutal-jobs.json")) {
            JsonNode root = JSON.readTree(stream);

            List<PipelineJob> jobs = new ArrayList<>();
            root.get("jobs")
                    .forEach(j -> jobs.add(new PipelineJob(
                            j.get("id").asText(),
                            j.get("name").asText(),
                            j.get("status").asText(),
                            j.get("standing").asBoolean(),
                            j.get("shape_eligible").asBoolean(),
                            j.get("is_rework").asBoolean(),
                            j.get("rework_of_job_id").isNull()
                                    ? null
                                    : j.get("rework_of_job_id").asText(),
                            j.get("counterparty_kind").isNull()
                                    ? null
                                    : j.get("counterparty_kind").asText(),
                            LocalDate.parse(j.get("last_activity_at").asText()))));

            List<ProcessShape> processes = new ArrayList<>();
            root.get("processes").forEach(p -> {
                List<String> steps = new ArrayList<>();
                p.get("steps").forEach(s -> steps.add(s.asText()));
                processes.add(
                        new ProcessShape(p.get("id").asText(), p.get("name").asText(), steps));
            });

            Map<String, Expected> expected = new LinkedHashMap<>();
            root.get("expected")
                    .forEach(e -> expected.put(
                            e.get("jobId").asText(),
                            new Expected(
                                    e.get("tier").asText(),
                                    e.get("why").asText(),
                                    e.get("scope").isNull()
                                            ? null
                                            : e.get("scope").asText())));

            Map<String, List<PipelineNode>> byJob = new LinkedHashMap<>();
            for (PipelineNode node : nodes.nodes()) {
                byJob.computeIfAbsent(node.jobId(), key -> new ArrayList<>()).add(node);
            }

            return new Fixture(nodes, jobs, processes, byJob, expected);
        }
    }
}
