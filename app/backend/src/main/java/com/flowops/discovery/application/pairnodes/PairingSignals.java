package com.flowops.discovery.application.pairnodes;

import com.flowops.discovery.domain.enums.OutputType;
import com.flowops.discovery.domain.model.WorkNode;
import java.util.UUID;

public record PairingSignals(
        boolean inTheSameThread,
        boolean bySamePerformer,
        boolean carriesATerminalOutput,
        boolean answersTheMostRecentOpenRequest) {
    public int matched() {
        int count = 0;
        if (inTheSameThread) {
            count++;
        }
        if (bySamePerformer) {
            count++;
        }
        if (carriesATerminalOutput) {
            count++;
        }
        if (answersTheMostRecentOpenRequest) {
            count++;
        }
        return count;
    }

    public static int howManyThereAre() {
        return 4;
    }

    public static PairingSignals between(WorkNode request, WorkNode completion, boolean isTheMostRecentOpenRequest) {
        return new PairingSignals(
                sameThread(request, completion),
                performerOf(request) != null && performerOf(request).equals(performerOf(completion)),
                completion.outputType().filter(OutputType::closesTheNode).isPresent(),
                isTheMostRecentOpenRequest);
    }

    private static boolean sameThread(WorkNode request, WorkNode completion) {
        return request.track().isPresent()
                && completion.track().isPresent()
                && request.track().get().equals(completion.track().get());
    }

    private static UUID performerOf(WorkNode node) {
        return node.performerId().orElse(node.creatorId());
    }
}
