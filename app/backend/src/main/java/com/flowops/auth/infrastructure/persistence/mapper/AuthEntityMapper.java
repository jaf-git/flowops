package com.flowops.auth.infrastructure.persistence.mapper;

import com.flowops.auth.domain.enums.AccountState;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.Credential;
import com.flowops.auth.domain.model.DisplayName;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.ResetToken;
import com.flowops.auth.domain.model.RoleName;
import com.flowops.auth.domain.model.SignupPasscode;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import com.flowops.auth.infrastructure.persistence.entity.AuthCredentialJpaEntity;
import com.flowops.auth.infrastructure.persistence.entity.AuthEventJpaEntity;
import com.flowops.auth.infrastructure.persistence.entity.AuthPasswordResetTokenJpaEntity;
import com.flowops.auth.infrastructure.persistence.entity.AuthSignupPasscodeJpaEntity;
import com.flowops.auth.infrastructure.persistence.entity.AuthUserJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class AuthEntityMapper {
    public AuthUserJpaEntity toEntity(User user) {
        return new AuthUserJpaEntity(
                user.id().value(),
                user.email().value(),
                user.accountState().name(),
                user.role().value(),
                user.createdAt(),
                user.setupCompleted(),
                user.displayName().map(DisplayName::value).orElse(null));
    }

    public User toDomain(AuthUserJpaEntity entity) {
        return User.rebuild(
                UserId.of(entity.getId()),
                new EmailAddress(entity.getEmail()),
                AccountState.valueOf(entity.getAccountState()),
                new RoleName(entity.getRoleName()),
                entity.getCreatedAt(),
                entity.isSetupCompleted(),
                entity.getDisplayName() == null ? null : new DisplayName(entity.getDisplayName()));
    }

    public AuthCredentialJpaEntity toEntity(Credential credential) {
        return new AuthCredentialJpaEntity(
                credential.userId().value(), credential.passwordHash(), credential.algorithm(), credential.updatedAt());
    }

    public Credential toDomain(AuthCredentialJpaEntity entity) {
        return Credential.rebuild(
                UserId.of(entity.getUserId()), entity.getPasswordHash(), entity.getAlgorithm(), entity.getUpdatedAt());
    }

    public AuthSignupPasscodeJpaEntity toEntity(SignupPasscode passcode) {
        return new AuthSignupPasscodeJpaEntity(
                passcode.id(),
                passcode.email().value(),
                passcode.codeHash(),
                passcode.issuedAt(),
                passcode.expiresAt(),
                passcode.used(),
                passcode.failureCount());
    }

    public SignupPasscode toDomain(AuthSignupPasscodeJpaEntity entity) {
        return SignupPasscode.rebuild(
                entity.getId(),
                new EmailAddress(entity.getEmail()),
                entity.getCodeHash(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.isUsed(),
                entity.getFailureCount());
    }

    public AuthEventJpaEntity toEntity(AuthEvent event) {
        return new AuthEventJpaEntity(
                event.id(),
                event.action().name(),
                event.actor().map(UserId::value).orElse(null),
                event.target().map(UserId::value).orElse(null),
                event.subject().map(EmailAddress::value).orElse(null),
                event.occurredAt(),
                event.metadata().ipAddress(),
                event.metadata().deviceSummary(),
                event.metadata().coarseLocation());
    }

    public ResetToken toDomain(AuthPasswordResetTokenJpaEntity entity) {
        return ResetToken.rebuild(
                entity.getId(),
                new UserId(entity.getUserId()),
                entity.getTokenHash(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getSpentAt());
    }

    public AuthPasswordResetTokenJpaEntity toEntity(ResetToken token) {
        return new AuthPasswordResetTokenJpaEntity(
                token.id(),
                token.userId().value(),
                token.tokenHash(),
                token.issuedAt(),
                token.expiresAt(),
                token.spentAt());
    }
}
