package com.flowops.auth.infrastructure.mail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.flowops.auth.AuthIntegrationTest;
import com.flowops.auth.application.shared.port.SendPasscodePort;
import com.flowops.auth.domain.model.EmailAddress;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("AUTH-REGISTER-OWNER-01")
class PasscodeDispatchOrderingIntegrationTest extends AuthIntegrationTest {
    private static final EmailAddress RECIPIENT = new EmailAddress("founder@flowops.test");

    @Autowired
    private SendPasscodePort sendPasscodePort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void nothingIsDispatchedBeforeTheTransactionCommits() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            sendPasscodePort.sendPasscode(RECIPIENT, "123456");
            verifyNoInteractions(mailDispatcher);
        });

        verify(mailDispatcher).send(any(EmailAddress.class), anyString(), anyString());
    }

    @Test
    void aRolledBackIssuingTransactionSendsNoPasscode() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            sendPasscodePort.sendPasscode(RECIPIENT, "123456");
            status.setRollbackOnly();
        });

        verify(mailDispatcher, never()).send(any(EmailAddress.class), anyString(), anyString());
    }

    @Test
    void aRolledBackIssuingTransactionSendsNoDuplicateNoticeEither() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            sendPasscodePort.sendDuplicateSignupNotice(RECIPIENT);
            status.setRollbackOnly();
        });

        verify(mailDispatcher, never()).send(any(EmailAddress.class), anyString(), anyString());
    }

    @Test
    void aCommittedTransactionSendsTheDuplicateNotice() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> sendPasscodePort.sendDuplicateSignupNotice(RECIPIENT));

        verify(mailDispatcher).send(any(EmailAddress.class), anyString(), anyString());
    }

    @Test
    void aCallerOutsideAnyTransactionStillSends() {
        sendPasscodePort.sendPasscode(RECIPIENT, "123456");

        verify(mailDispatcher).send(any(EmailAddress.class), anyString(), anyString());
    }
}
