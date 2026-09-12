package com.flowops.auth.infrastructure.mail;

import com.flowops.auth.application.shared.AuthProperties;
import com.flowops.auth.domain.model.EmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AsyncMailDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncMailDispatcher.class);

    private final JavaMailSender mailSender;
    private final AuthProperties properties;

    public AsyncMailDispatcher(JavaMailSender mailSender, AuthProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async
    public void send(EmailAddress email, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.notificationSender());
        message.setTo(email.value());
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException failure) {
            LOG.error("Could not deliver an auth message; the account action itself was unaffected.", failure);
        }
    }
}
