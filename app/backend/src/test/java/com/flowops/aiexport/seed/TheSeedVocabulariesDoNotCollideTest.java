package com.flowops.aiexport.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.support.ApplicationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("AI-EXPORT-SEED-DEMO-HISTORY-01")
class TheSeedVocabulariesDoNotCollideTest extends ApplicationTest {
    private static final double RESEMBLANCE_FLOOR = 0.3;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("no sentence a conversation converts resembles a template the seed planted elsewhere")
    void conversationsDoNotResolveOntoPlantedTemplates() {
        List<String> planted = new ArrayList<>(SeedRuns.everyPlaybookStepTitle());
        planted.addAll(Errands.clientServices().everyTemplateTitle());
        planted.addAll(Errands.studio().everyTemplateTitle());

        List<String> collisions = new ArrayList<>();
        for (String sentence : SeedConversations.everyWorkSentence()) {
            for (String existing : planted) {
                double closeness = similarity(sentence, existing);
                if (closeness > RESEMBLANCE_FLOOR) {
                    collisions.add("%.2f  \"%s\"  ->  \"%s\"".formatted(closeness, sentence, existing));
                }
            }
        }

        assertThat(collisions)
                .describedAs(
                        """
                        A sentence the seed converts resembles a template the seed already planted, above the \
                        %.1f floor TemplateResolutionService resolves at. The conversion will reuse the planted \
                        template instead of producing an emergent one, so the demonstration will show fewer \
                        kinds of discovered work than it claims and nothing will say so. Reword one side.""",
                        RESEMBLANCE_FLOOR)
                .isEmpty();
    }

    @Test
    @DisplayName("two different kinds of conversation work do not collapse into one template")
    void conversationSentencesDoNotResembleEachOther() {
        List<String> sentences = SeedConversations.everyWorkSentence();

        List<String> collisions = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            for (int j = i + 1; j < sentences.size(); j++) {
                double closeness = similarity(sentences.get(i), sentences.get(j));
                if (closeness > RESEMBLANCE_FLOOR) {
                    collisions.add("%.2f  \"%s\"  ->  \"%s\"".formatted(closeness, sentences.get(i), sentences.get(j)));
                }
            }
        }

        assertThat(collisions)
                .describedAs("Two kinds of work would resolve to one template, so the seed produces fewer emergent"
                        + " templates than it has families and each carries more uses than it should.")
                .isEmpty();
    }

    @Test
    @DisplayName("enough kinds of work recur often enough to clear the estimate-divergence floor")
    void enoughFamiliesClearTheEvidenceFloor() {
        List<String> starved = new ArrayList<>();
        for (String sentence : SeedConversations.everyWorkSentence()) {
            int occurrences = SeedClosurePolicy.occurrencesNeededFor(sentence, 6);
            int closed = SeedClosurePolicy.closedOutOf(sentence, occurrences);
            if (closed < 6) {
                starved.add("\"%s\" closes %d of %d, short of six".formatted(sentence, closed, occurrences));
            }
        }

        assertThat(starved)
                .describedAs("A kind of work will not reach five closed tasks, so"
                        + " AI-INSIGHT-ESTIMATE-DIVERGENCE-01 will decline to discuss it.")
                .isEmpty();
    }

    private double similarity(String left, String right) {
        Double closeness = jdbc.queryForObject(
                "select similarity(unaccent(lower(?)), unaccent(lower(?)))", Double.class, left, right);
        return closeness == null ? 0.0 : closeness;
    }
}
