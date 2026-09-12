package com.flowops.canvas.infrastructure.process;

import com.flowops.canvas.application.shared.port.InstanceScopePort;
import com.flowops.process.application.streamvisibility.InstanceVisibilityUseCase;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InstanceScopeAdapter implements InstanceScopePort {
    private final InstanceVisibilityUseCase visibility;

    public InstanceScopeAdapter(InstanceVisibilityUseCase visibility) {
        this.visibility = visibility;
    }

    @Override
    public Optional<UUID> instanceOf(UUID task) {
        return visibility.instanceOf(task);
    }

    @Override
    public boolean mayView(UUID person, Set<String> permissions, UUID instance) {
        return visibility.mayView(person, permissions, instance);
    }
}
