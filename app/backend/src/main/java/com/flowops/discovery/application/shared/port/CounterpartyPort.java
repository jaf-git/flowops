package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.model.Counterparty;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CounterpartyPort {
    UUID nextId();

    void save(Counterparty counterparty);

    Optional<Counterparty> find(UUID id);

    List<Counterparty> all();

    Optional<Counterparty> findByName(String name);

    long engagementsFor(UUID id);

    void delete(UUID id);
}
