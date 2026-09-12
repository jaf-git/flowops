package com.flowops.auth.application.anonymiseperson;

import com.flowops.auth.application.shared.port.ErasePersonalTracesPort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.SaveCredentialPort;
import com.flowops.auth.application.shared.port.SaveUserPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AccountState;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnonymisePersonService implements AnonymisePersonUseCase {
    private static final String ERASED_ADDRESS_FORMAT = "erased+%s@erased.invalid";

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final SaveCredentialPort saveCredentialPort;
    private final SessionRegistryPort sessionRegistryPort;
    private final ErasePersonalTracesPort erasePersonalTracesPort;

    public AnonymisePersonService(
            LoadUserPort loadUserPort,
            SaveUserPort saveUserPort,
            SaveCredentialPort saveCredentialPort,
            SessionRegistryPort sessionRegistryPort,
            ErasePersonalTracesPort erasePersonalTracesPort) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.saveCredentialPort = saveCredentialPort;
        this.sessionRegistryPort = sessionRegistryPort;
        this.erasePersonalTracesPort = erasePersonalTracesPort;
    }

    @Override
    @Transactional
    public void execute(UUID personId) {
        UserId id = UserId.of(personId);
        User user = loadUserPort.loadById(id).orElse(null);
        if (user == null || user.accountState() == AccountState.ERASED) {
            return;
        }

        EmailAddress theAddressBeingDestroyed = user.email();

        sessionRegistryPort.endEverySessionFor(id);
        saveCredentialPort.deleteFor(id);
        saveUserPort.save(user.anonymised(opaqueAddressFor(id)));

        erasePersonalTracesPort.eraseTracesOf(theAddressBeingDestroyed);
    }

    private EmailAddress opaqueAddressFor(UserId id) {
        return new EmailAddress(ERASED_ADDRESS_FORMAT.formatted(id.value()));
    }
}
