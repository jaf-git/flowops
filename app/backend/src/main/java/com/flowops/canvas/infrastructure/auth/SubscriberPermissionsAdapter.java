package com.flowops.canvas.infrastructure.auth;

import com.flowops.auth.application.describepermissions.DescribePermissionsUseCase;
import com.flowops.canvas.application.shared.port.SubscriberPermissionsPort;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SubscriberPermissionsAdapter implements SubscriberPermissionsPort {
    private final DescribePermissionsUseCase permissions;

    public SubscriberPermissionsAdapter(DescribePermissionsUseCase permissions) {
        this.permissions = permissions;
    }

    @Override
    public Set<String> heldBy(UUID person) {
        return permissions.heldBy(person);
    }
}
