package com.flowops.analyser.application.vocabulary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Says what a vocabulary merge left in the library, as a finding a person can read and dismiss.
 *
 * <p><b>Why this is a use case rather than a call into the finding model.</b> Merging two activities
 * happens in Discovery and findings belong to the analyser, and the architecture rule between them is
 * that one feature may call another's use cases and may never reach into its domain. So the caller
 * hands over names, identifiers and counts, and everything about how a finding is shaped — its
 * category, its severity, the words in it — is decided here, where findings live.
 *
 * <p><b>Why it exists at all.</b> A merge is entitled to change the graph and not the library.
 * {@code DECISION-NO-TEMPLATE-BINDING-01} says nothing binds instantiated work to its template, so a
 * person deciding that two words mean one thing is not evidence that a template they approved has
 * stopped describing real work. Retiring it automatically would be the first place this product
 * acted on an inference instead of asking — and it would do it on the artefact everyone reads.
 */
public interface RaiseMergeLeftoversUseCase {
    /**
     * Raises the finding, or does nothing if there is no completed analysis to attach it to.
     *
     * @return the finding's identifier where one was raised
     */
    java.util.Optional<UUID> execute(MergeLeftovers leftovers);

    record MergeLeftovers(String absorbedActivity, String survivingActivity, List<Template> templates, Instant at) {
        public MergeLeftovers {
            templates = templates == null ? List.of() : List.copyOf(templates);
        }
    }

    record Template(UUID id, String title, int timesUsed) {}
}
