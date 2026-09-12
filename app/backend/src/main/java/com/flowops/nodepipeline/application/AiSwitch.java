package com.flowops.nodepipeline.application;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * Whether the language model may be consulted at all, at runtime.
 *
 * <p>Until this existed the only way to turn the model off was to restart the server with different
 * properties, which meant nobody could answer the question a demonstration always raises — *what
 * does this look like without the AI?* — without leaving the room.
 *
 * <p>It gates every plug point together rather than one at a time. A half-on model is a
 * configuration nobody can reason about afterwards, and the question people actually ask is binary.
 *
 * <p>Deliberately not persisted. It is a switch on a running server, and a server that comes back up
 * in whatever state somebody left it in months ago is worse than one that comes back up in the state
 * its configuration describes.
 */
@Component
public class AiSwitch {
    private final AtomicBoolean on = new AtomicBoolean(true);

    public boolean isOn() {
        return on.get();
    }

    /**
     * Turns the model on or off for every plug point at once.
     *
     * @return what it was before, so a caller can tell a change from a no-op.
     */
    public boolean set(boolean wanted) {
        return on.getAndSet(wanted);
    }
}
