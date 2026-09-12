package com.flowops.discovery.application.analysis;

import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.RecommendationAlreadyDecidedException;
import com.flowops.discovery.application.shared.exception.UnknownRecommendationException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DismissProposalService implements DismissProposalUseCase {
    private final AnalysisStorePort store;
    private final IdentifyCallerPort caller;
    private final Clock clock;

    public DismissProposalService(AnalysisStorePort store, IdentifyCallerPort caller, Clock clock) {
        this.store = store;
        this.caller = caller;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void execute(UUID recommendationId) {
        UUID me = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        if (store.proposal(recommendationId).isEmpty()) {
            throw new UnknownRecommendationException("no proposal " + recommendationId + " to put aside");
        }

        if (!store.dismiss(recommendationId, me, clock.instant())) {
            throw new RecommendationAlreadyDecidedException(
                    "proposal " + recommendationId + " has already been decided");
        }
    }
}
