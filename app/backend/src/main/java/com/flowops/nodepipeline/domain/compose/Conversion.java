package com.flowops.nodepipeline.domain.compose;

import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.ai.LabelGrounding;
import com.flowops.nodepipeline.domain.discovery.DiscoveredProcess;
import com.flowops.nodepipeline.domain.discovery.StepKind;
import com.flowops.shared.text.Words;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Conversion {
    public static final double REUSE_SCORE_FLOOR = 0.70;

    public static final double REUSE_TEXT_FLOOR = 0.45;

    public static final double REUSE_MARGIN = 0.10;

    private static final double REPEATS_THAT_MEAN_REPEATABLE = 1.4;

    private static final double SIMULTANEOUS_AGREEMENT = 0.5;

    private Conversion() {}

    public enum Resolution {
        REUSED,

        REUSED_ASK,

        MINTED
    }

    public record Resolved(
            StepKind kind,
            Resolution outcome,
            String templateId,
            String title,
            double score,
            double margin,
            List<String> alternatives) {
        public Resolved {
            alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        }
    }

    public static Resolved resolve(StepKind kind, List<PipelineNode> kindNodes, List<CandidateTemplate> library) {
        List<Scored> scored = new ArrayList<>();

        for (CandidateTemplate template : library) {
            if ("RETIRED".equals(template.status())) {
                continue;
            }
            double role = kind.workType() != null
                            && (kind.workType().equals(template.responsibleRole())
                                    || kind.workType().equals(template.workType()))
                    ? 1.0
                    : 0.0;
            double output = template.outputKind() != null
                            && kind.outputType() != null
                            && kind.outputType().equals(template.outputKind())
                    ? 1.0
                    : 0.0;

            double text = textAgreement(kindNodes, template);
            scored.add(new Scored(template, 0.45 * text + 0.35 * role + 0.20 * output, text));
        }

        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparing(s -> s.template()
                .id()));

        if (scored.isEmpty()) {
            return new Resolved(kind, Resolution.MINTED, null, null, 0.0, 0.0, List.of());
        }

        Scored best = scored.get(0);
        double margin = best.score() - (scored.size() > 1 ? scored.get(1).score() : 0.0);
        List<String> alternatives =
                scored.stream().skip(1).limit(2).map(s -> s.template().id()).toList();

        if (best.score() >= REUSE_SCORE_FLOOR && best.text() >= REUSE_TEXT_FLOOR) {
            Resolution outcome = margin >= REUSE_MARGIN ? Resolution.REUSED : Resolution.REUSED_ASK;
            return new Resolved(
                    kind,
                    outcome,
                    best.template().id(),
                    best.template().title(),
                    round(best.score()),
                    round(margin),
                    alternatives);
        }

        return new Resolved(
                kind,
                Resolution.MINTED,
                null,
                null,
                round(best.score()),
                round(margin),
                scored.stream().limit(3).map(s -> s.template().id()).toList());
    }

    public static CandidateTemplate mint(StepKind kind, String id, LocalDate on) {
        return mint(kind, List.of(), id, on, null);
    }

    public static CandidateTemplate mint(
            StepKind kind,
            List<PipelineNode> nodes,
            String id,
            LocalDate on,
            java.util.function.Function<StepKind, String> namer) {
        Optional<String> comesFrom = agreedOn(nodes, PipelineNode::precedingRole);
        Optional<String> goesTo = agreedOn(nodes, PipelineNode::followingRole);

        return new CandidateTemplate(
                id,
                kind.namesAnActivity() && kind.activityName() != null
                        ? kind.activityName()
                        : whatPeopleCallIt(nodes).orElseGet(() -> titleFor(kind, namer)),
                describe(kind, nodes, comesFrom, goesTo),
                kind.workType(),
                whoNormallyDoesIt(nodes).orElse(kind.workType()),
                kind.outputType(),
                null,
                comesFrom.map("From %s."::formatted).orElse(null),
                goesTo.map("Handed to %s."::formatted).orElse(null),
                stepsPeopleWrote(nodes),
                "DRAFT",
                on,
                kind.words(),
                comesFrom.orElse(null),
                goesTo.orElse(null),
                agreedOn(nodes, PipelineNode::positionInTrack).orElse(null));
    }

    private static String describe(
            StepKind kind, List<PipelineNode> nodes, Optional<String> comesFrom, Optional<String> goesTo) {
        List<String> said = new ArrayList<>();

        whoNormallyDoesIt(nodes).ifPresent(role -> said.add("%s does this work.".formatted(role)));

        if (comesFrom.isPresent() && goesTo.isPresent()) {
            said.add("It normally follows %s and is handed to %s.".formatted(comesFrom.get(), goesTo.get()));
        } else {
            comesFrom.ifPresent(role -> said.add("It normally follows %s.".formatted(role)));
            goesTo.ifPresent(role -> said.add("It is normally handed to %s.".formatted(role)));
        }

        agreedOn(nodes, PipelineNode::positionInTrack)
                .ifPresent(at -> said.add("Usually the %s step of its thread.".formatted(ordinal(at + 1))));

        whatPeopleCallIt(nodes).ifPresent(name -> said.add("People doing it called it “%s”.".formatted(name)));

        if (!kind.words().isEmpty()) {
            said.add("The words that recur in it: %s.".formatted(String.join(", ", kind.words())));
        }

        said.add("Drafted from %d pieces of work across %d engagements."
                .formatted(kind.nodeIds().size(), kind.jobIds().size()));

        return String.join(" ", said);
    }

    private static <T> Optional<T> agreedOn(List<PipelineNode> nodes, java.util.function.Function<PipelineNode, T> of) {
        return commonest(nodes.stream()
                .map(of)
                .filter(value -> value != null && !(value instanceof String text && text.isBlank())));
    }

    private static String ordinal(int n) {
        if (n % 100 >= 11 && n % 100 <= 13) {
            return n + "th";
        }
        return switch (n % 10) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
    }

    private static Optional<String> whoNormallyDoesIt(List<PipelineNode> nodes) {
        return commonest(
                nodes.stream().map(PipelineNode::performerRole).filter(role -> role != null && !role.isBlank()));
    }

    private static Optional<String> whatPeopleCallIt(List<PipelineNode> nodes) {
        return commonest(nodes.stream().filter(PipelineNode::isDescribed).map(node -> node.title()
                .strip()));
    }

    private static List<String> stepsPeopleWrote(List<PipelineNode> nodes) {
        Map<String, Long> written = new LinkedHashMap<>();
        Map<String, String> spelling = new LinkedHashMap<>();

        for (PipelineNode node : nodes) {
            List<String> steps = node.checklist();
            if (steps == null) {
                continue;
            }
            for (String step : steps) {
                if (step == null || step.isBlank()) {
                    continue;
                }
                String key = step.strip().toLowerCase(java.util.Locale.ROOT);
                written.merge(key, 1L, Long::sum);
                spelling.putIfAbsent(key, step.strip());
            }
        }

        return written.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> spelling.get(entry.getKey()))
                .toList();
    }

    private static <T> Optional<T> commonest(java.util.stream.Stream<T> answers) {
        return answers
                .collect(java.util.stream.Collectors.groupingBy(
                        answer -> answer, LinkedHashMap::new, java.util.stream.Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    public static DraftProcess compose(
            DiscoveredProcess process,
            Map<String, Resolved> resolutions,
            Map<String, StepKind> kindsById,
            Map<String, List<PipelineNode>> nodesByJob,
            String processId,
            Map<String, String> templateStatuses) {
        Map<String, Integer> lanes = assignLanes(process, kindsById, nodesByJob);

        List<DraftProcess.DraftStep> steps = new ArrayList<>();
        List<String> core = process.core();

        for (int i = 0; i < core.size(); i++) {
            String kindId = core.get(i);
            Resolved resolved = resolutions.get(kindId);
            StepKind kind = kindsById.get(kindId);

            String templateId =
                    resolved != null && resolved.templateId() != null ? resolved.templateId() : "TT-DRAFT-" + (i + 1);

            double averageOccurrences = averageOccurrencesPerRun(process, kind, nodesByJob);
            boolean repeatable = averageOccurrences > REPEATS_THAT_MEAN_REPEATABLE;

            steps.add(new DraftProcess.DraftStep(
                    processId + "-S" + (i + 1),
                    kind != null && !kind.words().isEmpty() ? kind.words().get(0) : null,
                    resolved != null ? resolved.title() : null,
                    templateId,
                    templateStatuses.getOrDefault(templateId, "DRAFT"),
                    lanes.getOrDefault(kindId, i),
                    i + 1,
                    repeatable));
        }

        List<DraftProcess.DraftEdge> edges = new ArrayList<>();
        for (int i = 1; i < steps.size(); i++) {
            DraftProcess.DraftStep earlier = steps.get(i - 1);
            DraftProcess.DraftStep later = steps.get(i);
            if (earlier.lane() == later.lane()) {
                edges.add(new DraftProcess.DraftEdge(later.id(), earlier.id(), "OBSERVED", process.orderConfidence()));
            }
        }

        DraftProcess.Evidence evidence = new DraftProcess.Evidence(
                process.jobIds(),
                core.stream()
                        .map(kindsById::get)
                        .filter(java.util.Objects::nonNull)
                        .flatMap(k -> k.nodeIds().stream())
                        .limit(60)
                        .toList(),
                process.certainty(),
                process.orderConfidence(),
                List.of(
                        "ran %d times".formatted(process.runs()),
                        "the same %d steps every time".formatted(core.size()),
                        "in this order %.0f%% of the time".formatted(process.orderConfidence() * 100)));

        return new DraftProcess(
                processId, nameFor(core, resolutions), "DRAFT", "COMPOSED_FROM_DISCOVERY", steps, edges, evidence);
    }

    public static int composedEdgeCount(DraftProcess process) {
        return process.edges().size();
    }

    private static Map<String, Integer> assignLanes(
            DiscoveredProcess process, Map<String, StepKind> kindsById, Map<String, List<PipelineNode>> nodesByJob) {
        Map<String, List<LocalDate>> firstSeen = new LinkedHashMap<>();
        for (String jobId : process.jobIds()) {
            List<PipelineNode> jobNodes = nodesByJob.getOrDefault(jobId, List.of());
            for (String kindId : process.core()) {
                StepKind kind = kindsById.get(kindId);
                if (kind == null) {
                    continue;
                }
                jobNodes.stream()
                        .filter(node -> kind.nodeIds().contains(node.id()))
                        .map(PipelineNode::createdAt)
                        .min(Comparator.naturalOrder())
                        .ifPresent(earliest -> firstSeen
                                .computeIfAbsent(kindId, key -> new ArrayList<>())
                                .add(earliest));
            }
        }

        Map<String, Integer> lanes = new LinkedHashMap<>();
        int highestLane = 0;
        String previous = null;

        for (String kindId : process.core()) {
            if (previous == null) {
                lanes.put(kindId, 0);
            } else {
                List<LocalDate> before = firstSeen.getOrDefault(previous, List.of());
                List<LocalDate> now = firstSeen.getOrDefault(kindId, List.of());

                int compared = Math.min(before.size(), now.size());
                int together = 0;
                for (int i = 0; i < compared; i++) {
                    if (before.get(i).equals(now.get(i))) {
                        together++;
                    }
                }
                boolean simultaneous = compared > 0 && (double) together / compared >= SIMULTANEOUS_AGREEMENT;

                if (simultaneous) {
                    highestLane++;
                    lanes.put(kindId, highestLane);
                } else {
                    lanes.put(kindId, lanes.get(previous));
                }
            }
            previous = kindId;
        }
        return lanes;
    }

    private static double averageOccurrencesPerRun(
            DiscoveredProcess process, StepKind kind, Map<String, List<PipelineNode>> nodesByJob) {
        if (kind == null || process.jobIds().isEmpty()) {
            return 0;
        }
        int total = 0;
        for (String jobId : process.jobIds()) {
            total += (int) nodesByJob.getOrDefault(jobId, List.of()).stream()
                    .filter(node -> kind.nodeIds().contains(node.id()))
                    .count();
        }
        return (double) total / process.jobIds().size();
    }

    private static double textAgreement(List<PipelineNode> kindNodes, CandidateTemplate template) {
        double best = 0;
        for (PipelineNode node : kindNodes) {
            best = Math.max(best, Words.trigramOverlap(node.text(), template.title()));
            if (template.description() != null) {
                best = Math.max(best, Words.trigramOverlap(node.text(), template.description()));
            }
        }
        boolean keywordHit = template.keywords().stream().anyMatch(keyword -> {
            List<String> needed = Words.tokens(keyword);
            return !needed.isEmpty()
                    && needed.stream().allMatch(need -> kindNodes.stream()
                            .flatMap(node -> Words.tokens(node.text()).stream())
                            .anyMatch(word -> word.startsWith(need) || need.startsWith(word)));
        });
        return keywordHit ? Math.max(best, 0.85) : best;
    }

    static String titleFor(StepKind kind, java.util.function.Function<StepKind, String> namer) {
        if (namer == null) {
            return titleFor(kind);
        }
        return LabelGrounding.groundedLabel(namer.apply(kind), kind.words()).orElseGet(() -> titleFor(kind));
    }

    private static String titleFor(StepKind kind) {
        if (kind.namesAnActivity() && kind.activityName() != null) {
            return kind.activityName();
        }
        String subject = kind.words().isEmpty()
                ? kind.outputType()
                : String.join(
                        " ", kind.words().subList(0, Math.min(2, kind.words().size())));
        return readable(kind.workType()) + " — " + subject;
    }

    private static String nameFor(List<String> core, Map<String, Resolved> resolutions) {
        LinkedHashSet<String> leading = new LinkedHashSet<>();
        for (String kindId : core) {
            Resolved resolved = resolutions.get(kindId);
            String title = resolved == null ? null : resolved.title();
            if (title != null) {
                leading.add(title.split(" — ")[0]);
            }
            if (leading.size() == 3) {
                break;
            }
        }

        return leading.isEmpty() ? "Discovered process" : String.join(" · ", leading) + " …";
    }

    private static String readable(String code) {
        if (code == null || code.isBlank()) {
            return "Work";
        }
        String words = code.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static double round(double value) {
        return new java.math.BigDecimal(value)
                .setScale(3, java.math.RoundingMode.HALF_EVEN)
                .doubleValue();
    }

    private record Scored(CandidateTemplate template, double score, double text) {}
}
