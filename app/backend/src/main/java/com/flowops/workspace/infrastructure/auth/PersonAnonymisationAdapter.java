package com.flowops.workspace.infrastructure.auth;

import com.flowops.auth.application.anonymiseperson.AnonymisePersonUseCase;
import com.flowops.workspace.application.shared.port.AnonymisePersonPort;
import com.flowops.workspace.domain.model.PersonId;
import org.springframework.stereotype.Component;

@Component
public class PersonAnonymisationAdapter implements AnonymisePersonPort {
    private final AnonymisePersonUseCase anonymisePerson;

    public PersonAnonymisationAdapter(AnonymisePersonUseCase anonymisePerson) {
        this.anonymisePerson = anonymisePerson;
    }

    @Override
    public void anonymise(PersonId person) {
        anonymisePerson.execute(person.value());
    }
}
