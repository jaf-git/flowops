package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.application.ExplainDiscovery;
import com.flowops.nodepipeline.application.port.PipelineGraphPort;
import com.flowops.nodepipeline.application.port.WorkJudgePort;
import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.ai.Judgement;
import com.flowops.nodepipeline.domain.job.PipelineJob;
import com.flowops.nodepipeline.domain.job.ProcessShape;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExplainDiscoveryTest {

    private static CandidateTemplate aTemplate() {
        return new CandidateTemplate(
                "t-1",
                "Monthly write-up — October Iulius",
                "Scheduling and reporting does this work. The words that recur in it: agreed, iulius, month",
                "REPORTING",
                "Scheduling and reporting",
                "REPORT",
                null,
                null,
                null,
                List.of("Export the figures", "Write the commentary"),
                "DRAFT",
                LocalDate.of(2026, 8, 14),
                List.of("month", "iulius"),
                null,
                null,
                null);
    }

    /** Remembers what it was asked, so the prompt's contents can be asserted rather than assumed. */
    private static final class RecordingJudge implements WorkJudgePort {
        private final List<String> asked = new ArrayList<>();
        private final boolean on;

        private RecordingJudge(boolean on) {
            this.on = on;
        }

        @Override
        public boolean isAvailable() {
            return on;
        }

        @Override
        public boolean isEnabled(Judgement.PlugPoint plugPoint) {
            return on;
        }

        @Override
        public Optional<Judgement.Verdict> judge(Judgement.Question question) {
            asked.add(question.text());
            return Optional.of(new Judgement.Verdict("You will export the figures first.", 0.0, ""));
        }

        @Override
        public int callsRemaining() {
            return 10;
        }

        @Override
        public String modelId() {
            return "stub-model";
        }

        @Override
        public String promptVersion() {
            return "v3";
        }
    }

    private static final class OneTemplateAndOneProcess implements PipelineGraphPort {
        @Override
        public List<PipelineNode> nodesMarkedBetween(Instant from, Instant to, int limit) {
            return List.of();
        }

        @Override
        public java.util.Map<String, Integer> nodeCountsByJobBetween(Instant from, Instant to) {
            return java.util.Map.of();
        }

        @Override
        public List<CandidateTemplate> library() {
            return List.of();
        }

        @Override
        public java.util.Map<String, String> templateTitles() {
            return java.util.Map.of("t-a", "Brief", "t-b", "Design", "t-c", "Ship");
        }

        @Override
        public List<CandidateTemplate> discoveredTemplates() {
            return List.of(aTemplate());
        }

        @Override
        public Optional<String> commonestWorkTypeOf(UUID performer) {
            return Optional.empty();
        }

        @Override
        public Adoption activityAdoption() {
            return new Adoption(0, 0);
        }

        @Override
        public java.util.Set<String> directConversations() {
            return java.util.Set.of();
        }

        @Override
        public List<PipelineJob> jobsFor(java.util.Collection<String> jobIds) {
            return List.of();
        }

        @Override
        public List<ProcessShape> processShapes() {
            // A shape's steps are template ids, and one of them names a template nobody has.
            return List.of(new ProcessShape("p-1", "Client launch", List.of("t-a", "t-b", "t-gone", "t-c")));
        }
    }

    @Test
    void everythingTheePipelineFoundIsListedWithTheFactsItRestsOn() {
        ExplainDiscovery explain = new ExplainDiscovery(new OneTemplateAndOneProcess(), new RecordingJudge(true));

        List<ExplainDiscovery.Discovered> found = explain.everythingFound();

        assertThat(found).hasSize(2);
        assertThat(found).extracting(ExplainDiscovery.Discovered::kind).containsExactly("TEMPLATE", "PROCESS");
        assertThat(found.get(0).status())
                .as("drafts are included: what the pipeline proposed is a different question from what was approved")
                .isEqualTo("DRAFT");
        // The regression this guards: steps arrived as raw template ids, and a model handed them
        // wrote guidance telling a new starter to "write down the first ID, then the second".
        assertThat(found.get(1).steps())
                .as("ids become the names people gave the work, and one nobody has is left out "
                        + "rather than shown as a bare identifier")
                .containsExactly("Brief", "Design", "Ship");
    }

    @Test
    void theModelIsGivenTheObservationsAndNotTheMatchersVocabulary() {
        RecordingJudge judge = new RecordingJudge(true);
        ExplainDiscovery explain = new ExplainDiscovery(new OneTemplateAndOneProcess(), judge);

        Optional<ExplainDiscovery.Guidance> guidance = explain.explain("t-1");

        assertThat(guidance).isPresent();
        assertThat(guidance.get().writtenByModel())
                .as("everything this returns is a model's composition and says so")
                .isTrue();
        assertThat(guidance.get().modelId()).isEqualTo("stub-model");

        assertThat(judge.asked).singleElement().satisfies(prompt -> {
            assertThat(prompt).contains("Monthly write-up — October Iulius");
            assertThat(prompt).contains("Export the figures");
            assertThat(prompt).contains("Usually done by: Scheduling and reporting");

            // The regression this guards: the drafter's vocabulary sentence read as an
            // instruction, and the model told a new starter to write down the words that recur.
            assertThat(prompt).doesNotContain("The words that recur");
        });
    }

    @Test
    void nothingIsAskedWhenTheModelIsSwitchedOff() {
        RecordingJudge judge = new RecordingJudge(false);
        ExplainDiscovery explain = new ExplainDiscovery(new OneTemplateAndOneProcess(), judge);

        assertThat(explain.explain("t-1")).isEmpty();
        assertThat(judge.asked)
                .as("switched off means not asked, rather than asked and ignored")
                .isEmpty();
    }

    @Test
    void anIdNobodyFoundExplainsNothing() {
        ExplainDiscovery explain = new ExplainDiscovery(new OneTemplateAndOneProcess(), new RecordingJudge(true));

        assertThat(explain.explain("nothing-by-that-name")).isEmpty();
    }
}
