package com.flowops.discovery.application.absence;

import com.flowops.discovery.application.shared.port.PersonRolePort;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.domain.model.BracketInvariants;
import com.flowops.discovery.domain.model.WorkBracket;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClosureRightsFollowPeople {
    private static final long EVERY_HOUR = 3_600_000L;

    private static final int LONGEST_PLAUSIBLE_MANAGEMENT_LINE = 20;

    private final WorkBracketPort brackets;
    private final PersonRolePort people;

    public ClosureRightsFollowPeople(WorkBracketPort brackets, PersonRolePort people) {
        this.brackets = brackets;
        this.people = people;
    }

    @Scheduled(fixedDelay = EVERY_HOUR)
    @Transactional
    public Reconciled sweep() {
        int walkedUp = 0;
        int returned = 0;

        for (WorkBracket bracket : brackets.liveWorkEverywhere()) {
            if (!people.isActiveMember(bracket.closureRight())) {
                walkedUp += walkUp(bracket) ? 1 : 0;
            } else if (returnHome(bracket)) {
                returned++;
            }
        }

        return new Reconciled(walkedUp, returned);
    }

    private boolean walkUp(WorkBracket bracket) {
        Optional<UUID> nextActiveManager = firstActiveAbove(bracket.closureRight());

        if (nextActiveManager.isEmpty()) {
            return false;
        }

        bracket.closureWalksUpTo(nextActiveManager.get());
        brackets.save(bracket);

        BracketInvariants.everyOpenBracketIsClosable(bracket);
        return true;
    }

    private boolean returnHome(WorkBracket bracket) {
        Optional<UUID> absentee = bracket.closureStandsInFor();

        if (absentee.isEmpty() || !people.isActiveMember(absentee.get())) {
            return false;
        }

        bracket.closureReturnsTo(absentee.get());
        brackets.save(bracket);

        BracketInvariants.everyOpenBracketIsClosable(bracket);
        return true;
    }

    private Optional<UUID> firstActiveAbove(UUID person) {
        UUID walker = person;

        for (int rung = 0; rung < LONGEST_PLAUSIBLE_MANAGEMENT_LINE; rung++) {
            Optional<UUID> manager = people.managerOf(walker);

            if (manager.isEmpty()) {
                return Optional.empty();
            }

            if (people.isActiveMember(manager.get())) {
                return manager;
            }

            walker = manager.get();
        }

        return Optional.empty();
    }

    public record Reconciled(int walkedUp, int returnedToTheirOwner) {
        public boolean changedAnything() {
            return walkedUp > 0 || returnedToTheirOwner > 0;
        }
    }
}
