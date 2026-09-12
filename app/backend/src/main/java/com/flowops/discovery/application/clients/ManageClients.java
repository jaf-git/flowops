package com.flowops.discovery.application.clients;

import com.flowops.discovery.application.shared.port.CounterpartyPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.CounterpartyKind;
import com.flowops.discovery.domain.model.Counterparty;
import com.flowops.discovery.domain.model.Job;
import com.flowops.discovery.domain.model.JobId;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageClients {
    private final CounterpartyPort counterparties;
    private final WorkGraphPort graph;
    private final Clock clock;

    public ManageClients(CounterpartyPort counterparties, WorkGraphPort graph, Clock clock) {
        this.counterparties = counterparties;
        this.graph = graph;
        this.clock = clock;
    }

    @Transactional
    public Counterparty add(String name) {
        return counterparties.findByName(name).orElseGet(() -> {
            Counterparty created = Counterparty.named(counterparties.nextId(), name);
            counterparties.save(created);
            return created;
        });
    }

    @Transactional(readOnly = true)
    public List<Counterparty> all() {
        return counterparties.all();
    }

    @Transactional
    public void rename(UUID id, String name) {
        Counterparty client = mustFind(id);

        counterparties.findByName(name).filter(other -> !other.id().equals(id)).ifPresent(other -> {
            throw new ClientNameTakenException(name);
        });

        client.renamedTo(name);
        counterparties.save(client);
    }

    @Transactional
    public void classify(UUID id, CounterpartyKind kind, UUID by) {
        Counterparty client = mustFind(id);
        client.classifiedAs(kind, by, clock.instant());
        counterparties.save(client);
    }

    @Transactional
    public void remove(UUID id) {
        mustFind(id);

        long engagements = counterparties.engagementsFor(id);
        if (engagements > 0) {
            throw new ClientHasEngagementsException(id, engagements);
        }
        counterparties.delete(id);
    }

    @Transactional
    public void engagementIsFor(JobId job, UUID counterpartyId) {
        Counterparty client = mustFind(counterpartyId);

        Job engagement = graph.findJob(job).orElseThrow(() -> new UnknownClientException(counterpartyId));
        engagement.belongsTo(client.id());
        graph.save(engagement);
    }

    private Counterparty mustFind(UUID id) {
        return counterparties.find(id).orElseThrow(() -> new UnknownClientException(id));
    }
}
