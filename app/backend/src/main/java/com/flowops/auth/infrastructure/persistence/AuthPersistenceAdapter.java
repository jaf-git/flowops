package com.flowops.auth.infrastructure.persistence;

import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.ErasePersonalTracesPort;
import com.flowops.auth.application.shared.port.LoadCredentialPort;
import com.flowops.auth.application.shared.port.LoadPasscodePort;
import com.flowops.auth.application.shared.port.LoadResetTokenPort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.ResolvePermissionsPort;
import com.flowops.auth.application.shared.port.SaveCredentialPort;
import com.flowops.auth.application.shared.port.SavePasscodePort;
import com.flowops.auth.application.shared.port.SaveResetTokenPort;
import com.flowops.auth.application.shared.port.SaveUserPort;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.Credential;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.ResetToken;
import com.flowops.auth.domain.model.RoleName;
import com.flowops.auth.domain.model.SignupPasscode;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import com.flowops.auth.infrastructure.persistence.mapper.AuthEntityMapper;
import com.flowops.auth.infrastructure.persistence.repository.AuthCredentialJpaRepository;
import com.flowops.auth.infrastructure.persistence.repository.AuthEventJpaRepository;
import com.flowops.auth.infrastructure.persistence.repository.AuthLoginAttemptJpaRepository;
import com.flowops.auth.infrastructure.persistence.repository.AuthPasswordResetTokenJpaRepository;
import com.flowops.auth.infrastructure.persistence.repository.AuthSignupPasscodeJpaRepository;
import com.flowops.auth.infrastructure.persistence.repository.AuthUserJpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AuthPersistenceAdapter
        implements LoadUserPort,
                SaveUserPort,
                LoadCredentialPort,
                SaveCredentialPort,
                LoadPasscodePort,
                SavePasscodePort,
                AppendAuthEventPort,
                LoadResetTokenPort,
                SaveResetTokenPort,
                ErasePersonalTracesPort,
                ResolvePermissionsPort {
    private final AuthUserJpaRepository userRepository;
    private final AuthCredentialJpaRepository credentialRepository;
    private final AuthSignupPasscodeJpaRepository passcodeRepository;
    private final AuthEventJpaRepository eventRepository;
    private final AuthPasswordResetTokenJpaRepository resetTokenRepository;
    private final AuthLoginAttemptJpaRepository loginAttemptRepository;
    private final AuthEntityMapper mapper;

    public AuthPersistenceAdapter(
            AuthUserJpaRepository userRepository,
            AuthCredentialJpaRepository credentialRepository,
            AuthSignupPasscodeJpaRepository passcodeRepository,
            AuthEventJpaRepository eventRepository,
            AuthPasswordResetTokenJpaRepository resetTokenRepository,
            AuthLoginAttemptJpaRepository loginAttemptRepository,
            AuthEntityMapper mapper) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passcodeRepository = passcodeRepository;
        this.eventRepository = eventRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> loadByEmail(EmailAddress email) {
        return userRepository.findByEmail(email.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<User> loadById(UserId userId) {
        return userRepository.findById(userId.value()).map(mapper::toDomain);
    }

    @Override
    public List<User> loadAllById(Collection<UserId> userIds) {
        List<UUID> ids = userIds.stream().map(UserId::value).toList();
        return userRepository.findAllById(ids).stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        return userRepository.existsByEmail(email.value());
    }

    @Override
    public boolean anOwnerExists() {
        return userRepository.existsByRoleName(RoleName.OWNER.value());
    }

    @Override
    public void save(User user) {
        userRepository.save(mapper.toEntity(user));
    }

    @Override
    public Optional<Credential> loadFor(UserId userId) {
        return credentialRepository.findById(userId.value()).map(mapper::toDomain);
    }

    @Override
    public void save(Credential credential) {
        credentialRepository.save(mapper.toEntity(credential));
    }

    @Override
    public void deleteFor(UserId userId) {
        credentialRepository.deleteById(userId.value());
    }

    @Override
    public void eraseTracesOf(EmailAddress address) {
        eventRepository.forgetSubjectAddress(address.value());
        loginAttemptRepository.deleteBySubject(address.value());
    }

    @Override
    public Optional<SignupPasscode> loadLatestFor(EmailAddress email) {
        return passcodeRepository
                .findFirstByEmailOrderByIssuedAtDesc(email.value())
                .map(mapper::toDomain);
    }

    @Override
    public void save(SignupPasscode passcode) {
        passcodeRepository.save(mapper.toEntity(passcode));
    }

    @Override
    public void append(AuthEvent event) {
        eventRepository.save(mapper.toEntity(event));
    }

    @Override
    public Set<String> resolveFor(UserId userId) {
        return Set.copyOf(userRepository.findPermissionNames(userId.value()));
    }

    @Override
    public Optional<ResetToken> loadByTokenHash(String tokenHash) {
        return resetTokenRepository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    public void save(ResetToken token) {
        resetTokenRepository.save(mapper.toEntity(token));
    }
}
