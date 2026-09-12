package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.compose.Conversion;
import com.flowops.nodepipeline.domain.compose.DraftProcess;
import com.flowops.nodepipeline.domain.compose.ProcessValidator;
import com.flowops.nodepipeline.domain.discovery.DiscoveredProcess;
import com.flowops.nodepipeline.domain.discovery.StepKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversionTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 30);

    @Test
    void anExistingTemplateThatPlainlyDescribesTheWorkIsReused() {
        StepKind captions = kind("K:CONTENT:TEXT", "CONTENT", "TEXT", List.of("grocer", "captions"), "n1", "n2", "n3");
        List<PipelineNode> nodes = List.of(
                node("n1", "J1", "write the grocer captions for this week"),
                node("n2", "J2", "grocer captions drafted"),
                node("n3", "J3", "captions for the grocer, first pass"));

        Conversion.Resolved resolved = Conversion.resolve(
                captions,
                nodes,
                List.of(template(
                        "T-CAPS", "Write the weekly captions", "CONTENT", "TEXT", List.of("grocer captions"))));

        assertThat(resolved.outcome()).isEqualTo(Conversion.Resolution.REUSED);
        assertThat(resolved.templateId()).isEqualTo("T-CAPS");
    }

    @Test
    void whenNothingFitsADraftIsMintedAndItIsNeverApproved() {
        StepKind retro = kind("K:TEAM_LEAD:NONE", "TEAM_LEAD", "NONE", List.of("retro", "learned"), "n9");
        List<PipelineNode> nodes = List.of(node("n9", "J1", "quick retro on what we learned this month"));

        Conversion.Resolved resolved = Conversion.resolve(
                retro,
                nodes,
                List.of(template("T-CAPS", "Write the weekly captions", "CONTENT", "TEXT", List.of("captions"))));

        assertThat(resolved.outcome()).isEqualTo(Conversion.Resolution.MINTED);

        CandidateTemplate minted = Conversion.mint(retro, "TT-D01", TODAY);
        assertThat(minted.status())
                .as("no path in this system produces an APPROVED artefact")
                .isEqualTo("DRAFT");
        assertThat(minted.keywords()).containsExactlyElementsOf(retro.words());
        assertThat(minted.description()).contains("Drafted from");
    }

    @Test
    void roleAndOutputAgreementAloneDoNotCarryAReuse() {
        StepKind brief = kind("K:CONTENT:TEXT", "CONTENT", "TEXT", List.of("brief", "requirements"), "n1");
        List<PipelineNode> nodes = List.of(node("n1", "J1", "the client brief and what they asked for"));

        Conversion.Resolved resolved = Conversion.resolve(
                brief,
                nodes,
                List.of(template("T-CAPS", "Write the weekly captions", "CONTENT", "TEXT", List.of("captions"))));

        assertThat(resolved.outcome())
                .as("the role and output both agree, and that is not enough")
                .isEqualTo(Conversion.Resolution.MINTED);
    }

    @Test
    void aComposedProcessDrawsEdgesAndTheCountIsNotZero() {
        DraftProcess composed = composeThreeStepsRunOnDifferentDays();

        assertThat(Conversion.composedEdgeCount(composed))
                .as("zero edges is the documented failure, not a tidy parallel process")
                .isNotZero();

        assertThat(composed.edges()).allSatisfy(edge -> assertThat(edge.kind())
                .as("composition records order; only a person confirms it — ADR-004")
                .isEqualTo("OBSERVED"));
        assertThat(composed.blockingEdges())
                .as("and therefore nothing it drew can strand a step")
                .isEmpty();
    }

    @Test
    void stepsThatStartTogetherGetSeparateLanesAndNoEdgeBetweenThem() {
        DraftProcess composed = composeTwoStepsThatAlwaysStartTogether();

        assertThat(composed.steps().get(0).lane())
                .as("the first step opens lane 0")
                .isZero();
        assertThat(composed.steps().get(1).lane())
                .as("a step that runs alongside it opens a new lane")
                .isNotEqualTo(composed.steps().get(0).lane());
        assertThat(composed.edges())
                .as("work that happens at the same time has no order between it")
                .isEmpty();
    }

    @Test
    void aComposedProcessIsADraftThatCarriesItsEvidence() {
        DraftProcess composed = composeThreeStepsRunOnDifferentDays();

        assertThat(composed.status()).isEqualTo("DRAFT");
        assertThat(composed.origin()).isEqualTo("COMPOSED_FROM_DISCOVERY");
        assertThat(composed.evidence().jobIds()).isNotEmpty();
        assertThat(composed.evidence().because())
                .anySatisfy(why -> assertThat(why).contains("ran 3 times"));

        assertThat(ProcessValidator.validate(composed, "DRAFT").isAllowed())
                .as("legal as a draft, which is what composition produces")
                .isTrue();
        assertThat(ProcessValidator.validate(composed, "APPROVED").isAllowed())
                .as("and refused for approval, because its steps are drafts")
                .isFalse();
    }

    private static DraftProcess composeThreeStepsRunOnDifferentDays() {
        StepKind a = kind("K:A", "ACCOUNTS", "TEXT", List.of("brief"), "a1", "a2", "a3");
        StepKind b = kind("K:B", "CONTENT", "TEXT", List.of("captions"), "b1", "b2", "b3");
        StepKind c = kind("K:C", "REPORTING", "REPORT", List.of("numbers"), "c1", "c2", "c3");

        Map<String, List<PipelineNode>> byJob = Map.of(
                "J1", List.of(nodeOn("a1", "J1", 1), nodeOn("b1", "J1", 3), nodeOn("c1", "J1", 5)),
                "J2", List.of(nodeOn("a2", "J2", 1), nodeOn("b2", "J2", 4), nodeOn("c2", "J2", 6)),
                "J3", List.of(nodeOn("a3", "J3", 2), nodeOn("b3", "J3", 5), nodeOn("c3", "J3", 7)));

        DiscoveredProcess process = new DiscoveredProcess(
                List.of("K:A", "K:B", "K:C"), List.of("J1", "J2", "J3"), 3, 0.78, false, 0.7, List.of());

        return Conversion.compose(
                process,
                Map.of(
                        "K:A", minted(a, "TT-D01"),
                        "K:B", minted(b, "TT-D02"),
                        "K:C", minted(c, "TT-D03")),
                Map.of("K:A", a, "K:B", b, "K:C", c),
                byJob,
                "PT-D01",
                Map.of());
    }

    private static DraftProcess composeTwoStepsThatAlwaysStartTogether() {
        StepKind a = kind("K:A", "PHOTO", "DESIGN", List.of("shoot"), "a1", "a2", "a3");
        StepKind b = kind("K:B", "DESIGN", "DESIGN", List.of("layout"), "b1", "b2", "b3");

        Map<String, List<PipelineNode>> byJob = Map.of(
                "J1", List.of(nodeOn("a1", "J1", 1), nodeOn("b1", "J1", 1)),
                "J2", List.of(nodeOn("a2", "J2", 2), nodeOn("b2", "J2", 2)),
                "J3", List.of(nodeOn("a3", "J3", 3), nodeOn("b3", "J3", 3)));

        DiscoveredProcess process =
                new DiscoveredProcess(List.of("K:A", "K:B"), List.of("J1", "J2", "J3"), 3, 0.5, false, 0.6, List.of());

        return Conversion.compose(
                process,
                Map.of("K:A", minted(a, "TT-D01"), "K:B", minted(b, "TT-D02")),
                Map.of("K:A", a, "K:B", b),
                byJob,
                "PT-D02",
                Map.of());
    }

    private static Conversion.Resolved minted(StepKind kind, String id) {
        return new Conversion.Resolved(kind, Conversion.Resolution.MINTED, id, "Draft — " + id, 0.2, 0.0, List.of());
    }

    private static StepKind kind(String id, String workType, String output, List<String> words, String... nodeIds) {
        return new StepKind(id, workType, output, null, List.of(nodeIds), Set.of("J1", "J2", "J3"), words, 0.4, 0.7);
    }

    private static PipelineNode node(String id, String jobId, String text) {
        return nodeOn(id, jobId, 1, text);
    }

    private static PipelineNode nodeOn(String id, String jobId, int day) {
        return nodeOn(id, jobId, day, "some work that happened");
    }

    private static PipelineNode nodeOn(String id, String jobId, int day, String text) {
        return new PipelineNode(
                id,
                jobId,
                text,
                null,
                UUID.nameUUIDFromBytes("sara".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                null,
                "WORK",
                null,
                null,
                LocalDate.of(2026, 6, day),
                PipelineNode.Closure.MARKED,
                "STANDALONE",
                false,
                "TEXT",
                "CONTENT",
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    private static CandidateTemplate template(
            String id, String title, String workType, String outputKind, List<String> keywords) {
        return new CandidateTemplate(
                id,
                title,
                "Everything the platforms want for a week of posts",
                workType,
                workType,
                outputKind,
                "A set of captions",
                null,
                null,
                List.of(),
                "APPROVED",
                LocalDate.of(2026, 1, 1),
                keywords,
                null,
                null,
                null);
    }
}
