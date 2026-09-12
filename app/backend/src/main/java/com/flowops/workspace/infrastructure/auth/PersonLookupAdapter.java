package com.flowops.workspace.infrastructure.auth;

import com.flowops.auth.application.findpersonbyemail.FindPersonByEmailUseCase;
import com.flowops.workspace.application.shared.port.FindPersonByEmailPort;
import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.PersonId;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PersonLookupAdapter implements FindPersonByEmailPort {
    private final FindPersonByEmailUseCase findPersonByEmailUseCase;

    public PersonLookupAdapter(FindPersonByEmailUseCase findPersonByEmailUseCase) {
        this.findPersonByEmailUseCase = findPersonByEmailUseCase;
    }

    @Override
    public Optional<PersonId> findPersonByEmail(EmailAddress email) {
        return findPersonByEmailUseCase.execute(email.value()).map(PersonId::of);
    }
}
