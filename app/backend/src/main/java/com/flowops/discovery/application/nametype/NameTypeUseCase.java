package com.flowops.discovery.application.nametype;

import java.util.UUID;

public interface NameTypeUseCase {
    void name(UUID typeId, String name);

    void dismiss(UUID typeId);
}
