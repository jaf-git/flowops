package com.flowops.analyser.domain;

import com.flowops.shared.text.WorkTypeWords;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class WorkName {
    public static final String NOT_NAMED = "not named";

    private WorkName() {}

    public static String of(String workType, Collection<Snapshot.Node> nodes) {
        return of(null, workType, nodes);
    }

    public static String of(String activityName, String workType, Collection<Snapshot.Node> nodes) {
        if (activityName != null && !activityName.isBlank()) {
            return activityName;
        }
        return commonestTitle(nodes).or(() -> humanised(workType)).orElse(NOT_NAMED);
    }

    public static Map<String, String> byShapeId(Snapshot.Shapes shapes, Collection<Snapshot.Node> nodes) {
        Map<String, Snapshot.Node> byId =
                nodes.stream().collect(Collectors.toMap(Snapshot.Node::id, node -> node, (first, second) -> first));

        Map<String, String> names = new LinkedHashMap<>();
        for (Snapshot.Shape shape : shapes.kinds()) {
            List<Snapshot.Node> clustered = shape.nodeIds().stream()
                    .map(byId::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            names.put(shape.id(), of(shape.activityName(), shape.workType(), clustered));
        }
        return names;
    }

    public static String named(String shapeId, Map<String, String> names) {
        return names.getOrDefault(shapeId, NOT_NAMED);
    }

    public static Optional<String> commonestTitle(Collection<Snapshot.Node> nodes) {
        Map<String, List<String>> byKey = nodes.stream()
                .map(Snapshot.Node::title)
                .filter(title -> TitleKey.of(title) != null)
                .collect(Collectors.groupingBy(TitleKey::of));

        return byKey.values().stream().max(Comparator.comparingInt(List::size)).flatMap(WorkName::commonest);
    }

    public static Optional<String> humanised(String workType) {
        return WorkTypeWords.of(workType);
    }

    private static Optional<String> commonest(List<String> values) {
        return values.stream().collect(Collectors.groupingBy(value -> value, Collectors.counting())).entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }
}
