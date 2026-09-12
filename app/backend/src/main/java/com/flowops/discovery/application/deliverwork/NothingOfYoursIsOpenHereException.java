package com.flowops.discovery.application.deliverwork;

import java.util.List;

public class NothingOfYoursIsOpenHereException extends RuntimeException {
    private final List<String> heldByOthers;

    public NothingOfYoursIsOpenHereException(List<String> heldByOthers) {
        super(
                heldByOthers.isEmpty()
                        ? "nothing of yours is open here"
                        : "the work open here is held by " + String.join(", ", heldByOthers));
        this.heldByOthers = List.copyOf(heldByOthers);
    }

    public List<String> heldByOthers() {
        return heldByOthers;
    }
}
