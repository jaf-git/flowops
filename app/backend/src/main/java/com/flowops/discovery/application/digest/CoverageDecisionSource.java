package com.flowops.discovery.application.digest;

import com.flowops.discovery.application.digest.WeeklyDigestUseCase.Decision;
import com.flowops.discovery.application.digest.WeeklyDigestUseCase.Kind;
import com.flowops.discovery.application.shared.port.DiscoveryThresholdPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CoverageDecisionSource implements DigestDecisionSource {
    private final DigestReadPort figures;
    private final DiscoveryThresholdPort thresholds;

    public CoverageDecisionSource(DigestReadPort figures, DiscoveryThresholdPort thresholds) {
        this.figures = figures;
        this.thresholds = thresholds;
    }

    @Override
    public List<Ranked> since(Instant window) {
        int floor = thresholds.thresholds().roleCoverageFloorPercent();
        Map<UUID, Long> workByRole = figures.workMillisByPerformerRoleSince(window);

        List<Ranked> quiet = new ArrayList<>();
        for (DigestReadPort.RoleActivity role : figures.roleActivitySince(window)) {
            if (role.coveragePercent() < floor) {
                quiet.add(new Ranked(
                        new Decision(Kind.A_ROLE_STOPPED_CLICKING, null, role.roleName(), 0),
                        workByRole.getOrDefault(role.roleId(), 0L)));
            }
        }
        return quiet;
    }
}
