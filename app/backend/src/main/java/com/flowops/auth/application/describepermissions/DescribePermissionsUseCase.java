package com.flowops.auth.application.describepermissions;

import java.util.Set;
import java.util.UUID;

public interface DescribePermissionsUseCase {
    Set<String> heldBy(UUID personId);
}
