package com.flowops.tasklib.application.published;

import com.flowops.shared.published.PublishedRefusal;
import com.flowops.shared.published.RefusalKind;
import com.flowops.tasklib.application.exception.NotTheAuthorException;
import com.flowops.tasklib.application.exception.TemplateNotFoundException;
import com.flowops.tasklib.domain.exception.IllegalTemplateTransitionException;

public final class TemplateRefusals {
    private TemplateRefusals() {}

    static void refusingAsPublished(Runnable work) {
        try {
            work.run();
        } catch (TemplateNotFoundException absent) {
            throw new PublishedRefusal(RefusalKind.NOT_FOUND, "TEMPLATE_NOT_FOUND", absent.getMessage(), absent);
        } catch (IllegalTemplateTransitionException itsState) {
            throw new PublishedRefusal(RefusalKind.CONFLICT, "TEMPLATE_STATE_REFUSES", itsState.getMessage(), itsState);
        } catch (NotTheAuthorException theirs) {
            throw new PublishedRefusal(RefusalKind.NOT_PERMITTED, "NOT_PERMITTED", theirs.getMessage(), theirs);
        }
    }
}
