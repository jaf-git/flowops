package com.flowops.auth.application.describepermissions;

import com.flowops.auth.application.shared.port.ResolvePermissionsPort;
import com.flowops.auth.domain.model.UserId;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DescribePermissionsService implements DescribePermissionsUseCase {
    private final ResolvePermissionsPort resolvePermissionsPort;

    public DescribePermissionsService(ResolvePermissionsPort resolvePermissionsPort) {
        this.resolvePermissionsPort = resolvePermissionsPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> heldBy(UUID personId) {
        return resolvePermissionsPort.resolveFor(UserId.of(personId));
    }
}
