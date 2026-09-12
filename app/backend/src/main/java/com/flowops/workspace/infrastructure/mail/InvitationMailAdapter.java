package com.flowops.workspace.infrastructure.mail;

import com.flowops.workspace.application.shared.port.SendInvitationPort;
import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.InvitationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class InvitationMailAdapter implements SendInvitationPort {
    private static final Logger LOG = LoggerFactory.getLogger(InvitationMailAdapter.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String acceptUrl;

    public InvitationMailAdapter(
            JavaMailSender mailSender,
            @Value("${flowops.mail.from:no-reply@flowops.local}") String from,
            @Value("${flowops.workspace.invitation.accept-url:http://localhost:5173/invitation}") String acceptUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.acceptUrl = acceptUrl;
    }

    @Override
    public void sendInvitation(EmailAddress to, InvitationToken token) {
        String body = "You have been invited to FlowOps. Accept here: " + acceptUrl + "?token=" + token.value();

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(to.value(), body);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(to.value(), body);
            }
        });
    }

    @Async
    void dispatch(String to, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("You have been invited to FlowOps");
            message.setText(body);
            mailSender.send(message);
        } catch (RuntimeException e) {
            LOG.warn("invitation message to {} could not be delivered; it can be resent", to, e);
        }
    }
}
