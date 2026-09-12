package com.flowops.discovery.application.digest;

import com.flowops.discovery.application.digest.DigestDecisionSource.Ranked;
import com.flowops.discovery.application.proposetype.ProposeTypeUseCase;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.domain.enums.TrackTypeStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyDigestService implements WeeklyDigestUseCase {
    private static final Duration ONE_WEEK = Duration.ofDays(7);

    private static final int DECISIONS_A_WEEK = 3;

    private final IdentifyCallerPort caller;
    private final ProposeTypeUseCase proposals;
    private final DigestReadPort figures;
    private final List<DigestDecisionSource> sources;
    private final Clock clock;

    public WeeklyDigestService(
            IdentifyCallerPort caller,
            ProposeTypeUseCase proposals,
            DigestReadPort figures,
            List<DigestDecisionSource> sources,
            Clock clock) {
        this.caller = caller;
        this.proposals = proposals;
        this.figures = figures;
        this.sources = sources;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Digest execute() {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        Instant since = clock.instant().minus(ONE_WEEK);
        int waitingForTheOwner = (int) proposals.execute().stream()
                .filter(type -> type.status() == TrackTypeStatus.PROPOSED)
                .count();

        return new Digest(
                figures.threadsClosedSince(since),
                waitingForTheOwner,
                coverageAcrossTheWorkspace(since),
                theThreeThatMatterMost(since));
    }

    private int coverageAcrossTheWorkspace(Instant since) {
        int holders = 0;
        int contributors = 0;
        for (DigestReadPort.RoleActivity role : figures.roleActivitySince(since)) {
            holders += role.peopleHoldingIt();
            contributors += role.peopleWhoMarkedWork();
        }
        return holders == 0 ? 100 : (contributors * 100) / holders;
    }

    private List<Decision> theThreeThatMatterMost(Instant since) {
        List<Ranked> everything = new ArrayList<>();
        for (DigestDecisionSource source : sources) {
            everything.addAll(source.since(since));
        }
        everything.sort(Comparator.comparingLong(Ranked::value).reversed().thenComparing(ranked -> ranked.decision()
                .subject()));

        return everything.stream().limit(DECISIONS_A_WEEK).map(Ranked::decision).toList();
    }
}
