package com.flowops.discovery.application.undomark;

import java.util.UUID;

public class TheEngagementHoldsOtherWorkException extends RuntimeException {
    public TheEngagementHoldsOtherWorkException(UUID nodeId) {
        super("unit of work " + nodeId + " opened an engagement that now holds other people's work; "
                + "withdrawing it would leave that engagement without the boundary it is closed through");
    }
}
