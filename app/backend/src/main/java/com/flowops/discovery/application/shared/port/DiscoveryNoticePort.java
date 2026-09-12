package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.JobId;
import java.util.UUID;

public interface DiscoveryNoticePort {
    void awaitedWorkArrived(UUID waiter, BracketId waitersBracket);

    void awaitedWorkDied(UUID waiter, BracketId waitersBracket);

    void stillGoing(UUID closureHolder, BracketId bracket);

    void jobForceClosed(UUID holder, JobId job);

    void somebodyIsWaitingOnYou(UUID closureHolder, BracketId theirBracket);

    void theDateYouExpectedHasPassed(UUID waiter, BracketId waitersBracket);

    void thisHasBeenWaitingALongTime(UUID jobOwner, JobId job);
}
