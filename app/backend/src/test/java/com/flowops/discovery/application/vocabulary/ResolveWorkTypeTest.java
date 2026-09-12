package com.flowops.discovery.application.vocabulary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.flowops.discovery.application.shared.port.GovernedWorkTypePort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResolveWorkTypeTest {
    private final ResolveWorkType resolve = new ResolveWorkType(new StubVocabulary());

    @Test
    void aCanonicalCodeResolvesToItself() {
        assertThat(resolve.of("CONTENT")).contains("CONTENT");
        assertThat(resolve.of("content")).contains("CONTENT");
        assertThat(resolve.of("  Content  ")).contains("CONTENT");
    }

    @Test
    void anAliasResolvesToItsCanonicalCode() {
        assertThat(resolve.of("packshot")).contains("PHOTO");
        assertThat(resolve.of("Key Visual")).contains("DESIGN");
    }

    @Test
    void anUnmappedWordResolvesToNothingRatherThanToGeneral() {
        assertThat(resolve.of("sizzle")).isEmpty();
        assertThat(resolve.of("comps")).isEmpty();
        assertThat(resolve.of(null)).isEmpty();
        assertThat(resolve.of("   ")).isEmpty();
    }

    @Test
    void aRetiredTypeStillResolvesButIsNotOffered() {
        assertThat(resolve.of("DEV"))
                .as("history rendered with a blank where a kind of work used to be is worse than a retired label")
                .contains("DEV");

        assertThat(resolve.assignable())
                .extracting(GovernedWorkTypePort.WorkTypeEntry::code)
                .as("but nobody may choose it from now on")
                .doesNotContain("DEV")
                .contains("CONTENT", "DESIGN", "PHOTO");
    }

    @Test
    void theOfferedListKeepsItsEditorialOrder() {
        assertThat(resolve.assignable())
                .extracting(GovernedWorkTypePort.WorkTypeEntry::code)
                .containsExactly("CONTENT", "DESIGN", "PHOTO", "VIDEO");
    }

    @Test
    void nearnessIsSymmetricAndAnUnlistedPairScoresZero() {
        assertThat(resolve.nearness("PHOTO", "VIDEO")).isEqualTo(0.70, within(0.001));
        assertThat(resolve.nearness("VIDEO", "PHOTO"))
                .as("asked backwards, it is the same pair")
                .isEqualTo(0.70, within(0.001));

        assertThat(resolve.nearness("CONTENT", "PHOTO"))
                .as("nobody has related these two, so they are unrelated rather than unknown")
                .isEqualTo(0.0);
        assertThat(resolve.nearness("CONTENT", "CONTENT"))
                .as("a type is wholly itself, and that is not a row anybody should have to seed")
                .isEqualTo(1.0);
    }

    private static final class StubVocabulary implements GovernedWorkTypePort {
        @Override
        public List<WorkTypeEntry> vocabulary() {
            return List.of(
                    new WorkTypeEntry("CONTENT", "Content", true, 1),
                    new WorkTypeEntry("DESIGN", "Design", true, 2),
                    new WorkTypeEntry("PHOTO", "Photography", true, 3),
                    new WorkTypeEntry("VIDEO", "Video", true, 4),
                    new WorkTypeEntry("DEV", "Development", false, 5));
        }

        @Override
        public Map<String, String> aliases() {
            return Map.of("packshot", "PHOTO", "key visual", "DESIGN");
        }

        @Override
        public Map<TypePair, Double> family(int version) {
            return Map.of(TypePair.of("PHOTO", "VIDEO"), 0.70);
        }
    }
}
