package com.flowops.chat.application.buildprocess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DraftFromMessages {
    private static final int TITLE_LENGTH = 120;

    private static final int FEWEST = 2;

    private static final int MOST = 50;

    private static final Duration APART = Duration.ofDays(3);

    private final Clock clock;

    public DraftFromMessages(Clock clock) {
        this.clock = clock;
    }

    public ProcessDraft assemble(List<Ticked> ticked, UUID actor, Set<UUID> mayDirect) {
        if (ticked.size() < FEWEST) {
            throw new TooFewMessagesException();
        }
        if (ticked.size() > MOST) {
            throw new TooManyMessagesException(MOST, ticked.size());
        }

        List<Ticked> inThreadOrder =
                ticked.stream().sorted(Comparator.comparingLong(Ticked::seq)).toList();
        Instant from = clock.instant();

        List<ProcessDraft.Step> steps = new java.util.ArrayList<>();
        for (int at = 0; at < inThreadOrder.size(); at++) {
            Ticked said = inThreadOrder.get(at);
            steps.add(new ProcessDraft.Step(
                    said.messageId(),
                    Sourced.quoted(titleOf(said.body())),
                    said.body(),
                    Sourced.guessed(mayDirect.contains(said.authorId()) ? said.authorId() : actor),
                    Sourced.guessed(from.plus(APART.multipliedBy(at + 1L)))));
        }

        return new ProcessDraft(
                Sourced.quoted(titleOf(inThreadOrder.get(0).body())), Sourced.guessed(actor), List.copyOf(steps));
    }

    private static String titleOf(String body) {
        String line = body.lines().findFirst().orElse(body).strip();
        return line.length() <= TITLE_LENGTH ? line : line.substring(0, TITLE_LENGTH);
    }

    public record Ticked(UUID messageId, UUID authorId, String body, long seq) {}
}
