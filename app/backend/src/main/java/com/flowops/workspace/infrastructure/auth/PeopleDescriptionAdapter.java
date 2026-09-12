package com.flowops.workspace.infrastructure.auth;

import com.flowops.auth.application.describepeople.DescribePeopleUseCase;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.domain.model.PersonId;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PeopleDescriptionAdapter implements DescribePeoplePort {
    private final DescribePeopleUseCase describePeopleUseCase;

    public PeopleDescriptionAdapter(DescribePeopleUseCase describePeopleUseCase) {
        this.describePeopleUseCase = describePeopleUseCase;
    }

    @Override
    public List<PersonDescription> describe(Collection<PersonId> people) {
        return describePeopleUseCase
                .execute(people.stream().map(PersonId::value).toList())
                .stream()
                .map(described -> new PersonDescription(
                        PersonId.of(described.userId()), described.displayName(), described.role()))
                .toList();
    }
}
