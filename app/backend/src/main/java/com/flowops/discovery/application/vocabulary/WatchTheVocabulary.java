package com.flowops.discovery.application.vocabulary;

import com.flowops.discovery.application.shared.port.VocabularyReadPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchTheVocabulary {
    private static final Duration THE_WEEK_BEHIND = Duration.ofDays(8);

    private static final int RARE_ENOUGH_TO_QUESTION = 3;

    private final VocabularyReadPort vocabulary;
    private final Clock clock;

    public WatchTheVocabulary(VocabularyReadPort vocabulary, Clock clock) {
        this.vocabulary = vocabulary;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public VocabularyReadPort.Familiarity aboutToUse(String workType) {
        return vocabulary.familiarityOf(workType);
    }

    @Transactional(readOnly = true)
    public Weekly thisWeek() {
        Instant since = clock.instant().minus(THE_WEEK_BEHIND);

        return new Weekly(
                vocabulary.workTypesFirstUsedSince(since),
                vocabulary.counterpartiesAndProjectsFirstSeenSince(since),
                vocabulary.typesWorthMerging(RARE_ENOUGH_TO_QUESTION));
    }

    public record Weekly(
            List<VocabularyReadPort.FirstUse> newWorkTypes,
            List<VocabularyReadPort.FirstUse> newClientsAndProjects,
            List<VocabularyReadPort.MergeSuggestion> worthMerging) {
        public boolean hasAnything() {
            return !newWorkTypes.isEmpty() || !newClientsAndProjects.isEmpty() || !worthMerging.isEmpty();
        }
    }
}
