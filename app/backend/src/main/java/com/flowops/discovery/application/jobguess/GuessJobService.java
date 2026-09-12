package com.flowops.discovery.application.jobguess;

import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.MarkableMessagePort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.model.Job;
import com.flowops.discovery.domain.model.JobId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuessJobService implements GuessJobUseCase {
    private static final int OFFERED = 6;

    private final IdentifyCallerPort caller;
    private final WorkGraphPort graph;
    private final MarkableMessagePort messages;

    public GuessJobService(IdentifyCallerPort caller, WorkGraphPort graph, MarkableMessagePort messages) {
        this.caller = caller;
        this.graph = graph;
        this.messages = messages;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Offer> describing(UUID conversationId) {
        UUID me = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        if (conversationId == null || !messages.participatesIn(conversationId, me)) {
            return List.of();
        }

        return graph.jobsTouchedIn(conversationId, OFFERED).stream()
                .map(job -> new Offer(job.id(), job.name(), false))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Offer> execute(UUID conversationId) {
        UUID me = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        List<Job> fromThisConversation = conversationId != null && messages.participatesIn(conversationId, me)
                ? graph.openJobsTouchedIn(conversationId, OFFERED)
                : List.of();
        List<Job> fromTheWorkspace = graph.recentlyTouchedOpenJobs(OFFERED);

        JobId guess = firstOf(fromThisConversation, fromTheWorkspace);

        List<Offer> offers = new ArrayList<>();
        Set<JobId> alreadyOffered = new LinkedHashSet<>();
        for (Job job : concatenated(fromThisConversation, fromTheWorkspace)) {
            if (offers.size() == OFFERED) {
                break;
            }
            if (alreadyOffered.add(job.id())) {
                offers.add(new Offer(job.id(), job.name(), job.id().equals(guess)));
            }
        }
        return List.copyOf(offers);
    }

    private static JobId firstOf(List<Job> preferred, List<Job> fallback) {
        if (!preferred.isEmpty()) {
            return preferred.getFirst().id();
        }
        if (!fallback.isEmpty()) {
            return fallback.getFirst().id();
        }
        return null;
    }

    private static List<Job> concatenated(List<Job> first, List<Job> second) {
        List<Job> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
