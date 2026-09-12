package com.flowops.process.application.published;

import com.flowops.process.application.shared.exception.NotTheAuthorException;
import com.flowops.process.application.shared.exception.TemplateNameTakenException;
import com.flowops.process.application.shared.exception.TemplateNotFoundException;
import com.flowops.process.domain.exception.CycleWouldFormException;
import com.flowops.process.domain.exception.ProcessOwnerNotActiveException;
import com.flowops.process.domain.exception.StepWouldBeStrandedException;
import com.flowops.process.domain.exception.TemplateIsRetiredException;
import com.flowops.shared.published.PublishedRefusal;
import com.flowops.shared.published.RefusalKind;

public final class ProcessRefusals {
    static final String TEMPLATE_NOT_FOUND = "TEMPLATE_NOT_FOUND";
    static final String TEMPLATE_NOT_FOUND_SAYS = "There is no such process.";

    static final String NOT_THE_AUTHOR = "NOT_THE_AUTHOR";
    static final String NOT_THE_AUTHOR_SAYS =
            "That process is somebody else's to change. Ask whoever wrote it, or the owner.";

    static final String TEMPLATE_IS_RETIRED = "TEMPLATE_IS_RETIRED";
    static final String TEMPLATE_IS_RETIRED_SAYS =
            "This template has been retired. Runs already started from it are unaffected.";

    static final String PROCESS_OWNER_NOT_ACTIVE = "ASSIGNEE_NOT_ACTIVE";
    static final String PROCESS_OWNER_NOT_ACTIVE_SAYS = "That person is no longer active, so they cannot steer a run.";

    static final String GRAPH_CYCLE = "GRAPH_CYCLE";
    static final String GRAPH_CYCLE_SAYS = "Those steps would end up waiting for each other.";

    static final String STEP_STRANDED = "STEP_STRANDED";
    static final String STEP_STRANDED_SAYS = "That would leave a step nothing can ever reach.";

    static final String TEMPLATE_NAME_TAKEN = "TEMPLATE_NAME_TAKEN";
    static final String TEMPLATE_NAME_TAKEN_SAYS = "A process already goes by that name. Choose another.";

    private ProcessRefusals() {}

    static void refusingAsPublished(Runnable work) {
        try {
            work.run();
        } catch (TemplateNotFoundException absent) {
            throw published(RefusalKind.NOT_FOUND, TEMPLATE_NOT_FOUND, TEMPLATE_NOT_FOUND_SAYS, absent);
        } catch (NotTheAuthorException theirs) {
            throw published(RefusalKind.NOT_PERMITTED, NOT_THE_AUTHOR, NOT_THE_AUTHOR_SAYS, theirs);
        } catch (ProcessOwnerNotActiveException gone) {
            throw published(RefusalKind.CONFLICT, PROCESS_OWNER_NOT_ACTIVE, PROCESS_OWNER_NOT_ACTIVE_SAYS, gone);
        } catch (TemplateNameTakenException taken) {
            throw published(RefusalKind.CONFLICT, TEMPLATE_NAME_TAKEN, TEMPLATE_NAME_TAKEN_SAYS, taken);
        } catch (TemplateIsRetiredException retired) {
            throw published(RefusalKind.CONFLICT, TEMPLATE_IS_RETIRED, TEMPLATE_IS_RETIRED_SAYS, retired);
        } catch (CycleWouldFormException cycle) {
            throw published(RefusalKind.CONFLICT, GRAPH_CYCLE, GRAPH_CYCLE_SAYS, cycle);
        } catch (StepWouldBeStrandedException stranded) {
            throw published(RefusalKind.CONFLICT, STEP_STRANDED, STEP_STRANDED_SAYS, stranded);
        }
    }

    private static PublishedRefusal published(RefusalKind kind, String code, String says, RuntimeException cause) {
        return new PublishedRefusal(kind, code, says, cause);
    }
}
