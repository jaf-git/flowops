package com.flowops.auth.application.setdisplayname;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.auth.application.shared.exception.AuthenticationRefusedException;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.SaveUserPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.exception.DisplayNameRequiredException;
import com.flowops.auth.domain.model.DisplayName;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("WORKSPACE-SETUP-01")
@ExtendWith(MockitoExtension.class)
class SetDisplayNameServiceTest {
    @Mock
    private SessionRegistryPort sessionRegistryPort;

    @Mock
    private LoadUserPort loadUserPort;

    @Mock
    private SaveUserPort saveUserPort;

    @InjectMocks
    private SetDisplayNameService service;

    private final User owner =
            User.registerOwner(new EmailAddress("maria@atelier.ro"), Instant.parse("2026-08-01T09:00:00Z"));

    private void aSignedInOwner() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(owner.id()));
        when(loadUserPort.loadById(owner.id())).thenReturn(Optional.of(owner));
    }

    @Test
    void itNamesTheAccountBehindTheSession() {
        aSignedInOwner();

        service.execute(new SetDisplayNameCommand("Maria Ionescu"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(saveUserPort).save(saved.capture());
        assertThat(saved.getValue().displayName()).contains(new DisplayName("Maria Ionescu"));
        assertThat(saved.getValue().id()).isEqualTo(owner.id());
    }

    @Test
    void itReachesNoAccountButTheCallersOwn() {
        aSignedInOwner();

        service.execute(new SetDisplayNameCommand("Maria Ionescu"));

        verify(loadUserPort).loadById(owner.id());
        verify(loadUserPort, never()).loadById(UserId.generate());
    }

    @Test
    void aBlankNameIsRefusedAndNothingIsSaved() {
        aSignedInOwner();

        assertThatThrownBy(() -> service.execute(new SetDisplayNameCommand("   ")))
                .isInstanceOf(DisplayNameRequiredException.class);
        verify(saveUserPort, never()).save(any());
    }

    @Test
    void aCallerWithNoSessionIsRefused() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new SetDisplayNameCommand("Maria Ionescu")))
                .isInstanceOf(AuthenticationRefusedException.class);
        verify(saveUserPort, never()).save(any());
    }
}
