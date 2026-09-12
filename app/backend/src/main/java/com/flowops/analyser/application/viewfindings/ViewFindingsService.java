package com.flowops.analyser.application.viewfindings;

import com.flowops.analyser.application.shared.port.DismissalPort;
import com.flowops.analyser.application.shared.port.FindingReadPort;
import com.flowops.analyser.domain.Category;
import com.flowops.analyser.domain.Lifecycle;
import com.flowops.analyser.domain.PriorityRule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewFindingsService implements ViewFindingsUseCase {
    private final FindingReadPort findings;
    private final DismissalPort dismissals;
    private final PriorityRule priorityRule;

    public ViewFindingsService(
            FindingReadPort findings,
            DismissalPort dismissals,
            @Value("${flowops.analyser.priority.grace:2}") int grace,
            @Value("${flowops.analyser.priority.decay:0.8}") double decay,
            @Value("${flowops.analyser.priority.floor:0.25}") double floor) {
        this.findings = findings;
        this.dismissals = dismissals;
        this.priorityRule = new PriorityRule(grace, decay, floor);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Queue> execute() {
        return findings.latestFindings().map(this::queueOf);
    }

    private Queue queueOf(FindingReadPort.Run run) {
        Set<String> saidNo = dismissals.current().keySet();

        List<FindingReadPort.Row> shown =
                run.findings().stream().filter(row -> !isHeldBack(row, saidNo)).toList();

        Map<Category, List<Item>> byCategory = new EnumMap<>(Category.class);
        for (FindingReadPort.Row row : shown) {
            byCategory.computeIfAbsent(row.category(), key -> new ArrayList<>()).add(itemOf(row));
        }

        List<Group> groups = new ArrayList<>();

        for (Category category : Category.values()) {
            List<Item> items = byCategory.get(category);
            if (items == null) {
                continue;
            }
            items.sort(WORST_FIRST);
            groups.add(new Group(category, List.copyOf(items)));
        }

        return new Queue(
                run.runId(), run.windowFrom(), run.windowTo(), run.ranAt(), List.copyOf(groups), standing(run, saidNo));
    }

    private static boolean isHeldBack(FindingReadPort.Row row, Set<String> saidNo) {
        if (row.lifecycle() == Lifecycle.WORSENING) {
            return false;
        }
        return row.lifecycle() == Lifecycle.DISMISSED || saidNo.contains(row.key());
    }

    private static final Comparator<Item> WORST_FIRST = Comparator.comparingDouble(Item::priority)
            .reversed()
            .thenComparing(Comparator.comparingInt(Item::reach).reversed())
            .thenComparing(Item::headline);

    private Item itemOf(FindingReadPort.Row row) {
        PriorityRule.Ranked ranked = priorityRule.rank(
                row.severity(), row.confidence(), row.reach(), row.reachOf(), row.lifecycle(), row.timesSeen());

        List<Evidence> evidence = row.evidence().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(id -> new Evidence(entry.getKey().name(), id)))
                .toList();

        return new Item(
                row.id(),
                row.analyser(),
                row.kind(),
                row.stage(),
                row.subjectKind(),
                row.subject(),
                row.subjectName(),
                row.context(),
                row.headline(),
                row.because(),
                row.severity(),
                row.confidence(),
                row.reach(),
                row.reachOf(),
                row.action(),
                row.lifecycle(),
                row.timesSeen(),
                row.firstSeenAt(),
                ranked.score(),
                ranked.because(),
                evidence);
    }

    private static Standing standing(FindingReadPort.Run run, Set<String> saidNo) {
        Map<Lifecycle, Integer> shownByLifecycle = new EnumMap<>(Lifecycle.class);
        int dismissed = 0;
        for (FindingReadPort.Row row : run.findings()) {
            if (isHeldBack(row, saidNo)) {
                dismissed++;
            } else {
                shownByLifecycle.merge(row.lifecycle(), 1, Integer::sum);
            }
        }
        int fresh = shownByLifecycle.getOrDefault(Lifecycle.NEW, 0);
        int worsening = shownByLifecycle.getOrDefault(Lifecycle.WORSENING, 0);

        return new Standing(
                run.findings().size() - dismissed,
                fresh,
                worsening,
                shownByLifecycle.getOrDefault(Lifecycle.STILL_TRUE, 0),
                shownByLifecycle.getOrDefault(Lifecycle.IMPROVING, 0),
                dismissed,
                fresh == 0 && worsening == 0);
    }
}
