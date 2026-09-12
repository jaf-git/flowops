package com.flowops.auth.infrastructure.mail;

import com.flowops.auth.application.shared.AuthProperties;
import com.flowops.auth.application.shared.port.SendPasscodePort;
import com.flowops.auth.application.shared.port.SendResetLinkPort;
import com.flowops.auth.domain.model.EmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class MailpitPasscodeAdapter implements SendPasscodePort, SendResetLinkPort {
    private static final Logger LOG = LoggerFactory.getLogger(MailpitPasscodeAdapter.class);

    private final AsyncMailDispatcher asyncSender;
    private final AuthProperties properties;

    public MailpitPasscodeAdapter(AsyncMailDispatcher asyncSender, AuthProperties properties) {
        this.asyncSender = asyncSender;
        this.properties = properties;
    }

    @Override
    public void sendPasscode(EmailAddress email, String rawCode) {
        afterCommit(
                email,
                "Your FlowOps signup code",
                "Your signup code is " + rawCode + ". It expires shortly and can be used once.");
    }

    @Override
    public void sendDuplicateSignupNotice(EmailAddress email) {
        afterCommit(
                email,
                "A signup was attempted with your address",
                "Someone tried to create a FlowOps account with this address, which is already registered. "
                        + "No new account was created and no code was issued. "
                        + "If this was you, sign in instead. If it was not, you need do nothing.");
    }

    @Override
    public void sendResetLink(EmailAddress email, String rawToken) {
        afterCommit(
                email,
                "Reset your FlowOps password",
                "Open this link to choose a new password: " + properties.resetLinkBase() + "?token=" + rawToken
                        + System.lineSeparator() + System.lineSeparator()
                        + "It can be used once and expires shortly. Every device signed in to this account "
                        + "will be signed out when you finish."
                        + System.lineSeparator() + System.lineSeparator()
                        + "If you did not ask for this, you need do nothing -- your current password still works "
                        + "and this link will expire unused.");
    }

    private void afterCommit(EmailAddress email, String subject, String body) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            asyncSender.send(email, subject, body);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchQuietly(email, subject, body);
            }
        });
    }

    private void dispatchQuietly(EmailAddress email, String subject, String body) {
        try {
            asyncSender.send(email, subject, body);
        } catch (RuntimeException failure) {
            LOG.error(
                    "Could not hand an auth message to the sender; the account action had already committed.", failure);
        }
    }
}
