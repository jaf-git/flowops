package com.flowops.analyser.application.dismissfinding;

import com.flowops.analyser.application.shared.port.DismissalPort;
import com.flowops.analyser.application.shared.port.FindingReadPort;
import com.flowops.analyser.application.shared.port.IdentifyCallerPort;
import com.flowops.analyser.domain.FindingFingerprint;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DismissFindingService implements DismissFindingUseCase {
    private final FindingReadPort findings;
    private final DismissalPort dismissals;
    private final IdentifyCallerPort caller;
    private final Clock clock;

    public DismissFindingService(
            FindingReadPort findings, DismissalPort dismissals, IdentifyCallerPort caller, Clock clock) {
        this.findings = findings;
        this.dismissals = dismissals;
        this.caller = caller;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void execute(Dismiss command) {
        FindingReadPort.Row row =
                findings.byId(command.findingId()).orElseThrow(() -> new NoSuchFinding(command.findingId()));

        java.util.UUID decider = caller.currentCaller()
                .orElseThrow(() -> new IllegalStateException("a dismissal is made by a person, and there is none"));

        FindingFingerprint fingerprint =
                FindingFingerprint.of(row.severity(), row.confidence(), row.action(), row.reach(), row.reachOf());

        dismissals.dismiss(row.key(), row.analyser(), fingerprint.format(), decider, clock.instant());
    }
}
