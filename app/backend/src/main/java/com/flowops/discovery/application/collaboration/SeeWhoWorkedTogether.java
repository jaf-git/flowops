package com.flowops.discovery.application.collaboration;

import com.flowops.discovery.application.shared.port.CollaborationReadPort;
import com.flowops.discovery.domain.model.CollaborationTier;
import com.flowops.discovery.domain.model.JobId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeeWhoWorkedTogether {
    private final CollaborationReadPort collaborations;

    public SeeWhoWorkedTogether(CollaborationReadPort collaborations) {
        this.collaborations = collaborations;
    }

    @Transactional(readOnly = true)
    public List<Group> inJob(JobId job) {
        List<Group> groups = new ArrayList<>();
        Set<String> alreadyPaired = new HashSet<>();

        collect(collaborations.declaredJoins(job), CollaborationTier.DECLARED, groups, alreadyPaired);
        collect(collaborations.sharingAnOutput(job), CollaborationTier.STRONG, groups, alreadyPaired);
        collect(collaborations.askedForTogether(job), CollaborationTier.GOOD, groups, alreadyPaired);
        collect(collaborations.workingAlongside(job), CollaborationTier.WEAK, groups, alreadyPaired);

        return List.copyOf(groups);
    }

    @Transactional(readOnly = true)
    public List<CollaborationReadPort.Reuse> assetsThatCameForFree() {
        return collaborations.assetsReusedAcrossJobs();
    }

    private void collect(
            List<CollaborationReadPort.Pairing> pairings,
            CollaborationTier tier,
            List<Group> into,
            Set<String> alreadyPaired) {
        for (CollaborationReadPort.Pairing pairing : pairings) {
            String key = pairing.oneBracket() + ":" + pairing.otherBracket();

            if (alreadyPaired.add(key)) {
                into.add(new Group(
                        List.of(pairing.oneBracket(), pairing.otherBracket()),
                        pairing.workType(),
                        tier,
                        pairing.evidence()));
            }
        }
    }

    public record Group(List<UUID> brackets, String workType, CollaborationTier tier, String evidence) {
        public boolean collapsesToOneStep() {
            return tier.collapsesToOneStep();
        }
    }
}
