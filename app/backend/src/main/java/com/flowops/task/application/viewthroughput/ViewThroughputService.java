package com.flowops.task.application.viewthroughput;

import com.flowops.task.application.shared.TaskAudienceRule;
import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.domain.model.PersonId;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewThroughputService implements ViewThroughputUseCase {
    private static final int LONGEST = 52;

    private static final int SHORTEST = 4;

    private final IdentifyCallerPort identifyCallerPort;
    private final TaskAudienceRule taskAudienceRule;
    private final ThroughputReadPort throughputReadPort;
    private final Clock clock;

    public ViewThroughputService(
            IdentifyCallerPort identifyCallerPort,
            TaskAudienceRule taskAudienceRule,
            ThroughputReadPort throughputReadPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.taskAudienceRule = taskAudienceRule;
        this.throughputReadPort = throughputReadPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Week> of(int weeks) {
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        int bounded = Math.min(Math.max(weeks, SHORTEST), LONGEST);

        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusWeeks(bounded - 1L);

        return throughputReadPort.weeklyCounts(taskAudienceRule.forCaller(caller), from, today, clock.getZone());
    }
}
