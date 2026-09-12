package com.flowops.discovery.application.claimbracket;

import com.flowops.discovery.domain.model.BracketId;
import java.util.UUID;

public class WorkIsAlreadySomebodysException extends RuntimeException {
    public WorkIsAlreadySomebodysException(BracketId bracket, UUID performer) {
        super("bracket " + bracket.value() + " is already " + performer + "'s work; "
                + "R2.1 claims only unclaimed work, and taking somebody else's over is a handover");
    }
}
