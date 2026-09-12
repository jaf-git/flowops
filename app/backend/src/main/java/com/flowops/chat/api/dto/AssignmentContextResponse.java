package com.flowops.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Who the other person is, and what work may be given to them from here.")
public record AssignmentContextResponse(
        @Schema(description = "Absent on the announcement channel and on a team channel") UUID counterpartId,
        String counterpartName,
        @Schema(description = "False where they have been deactivated or erased") boolean counterpartActive,
        @Schema(
                        description = "TASK's answer, not CHAT's. False outside the caller's subtree and false for "
                                + "somebody deactivated. Drives whether the task control renders")
                boolean mayAssignTask,
        @Schema(
                        description = "PROCESS's answer, not CHAT's. Drives whether the process control renders. "
                                + "**Distinct from an empty `templates`**: not permitted and none yet "
                                + "send a person to do entirely different things")
                boolean mayStartRun,
        @Schema(
                        description = "Active templates the caller may start. Empty is an ordinary answer and the "
                                + "dialog says so rather than the control vanishing")
                List<Template> templates) {
    public record Template(
            UUID id,
            String name,
            String overview,
            @Schema(description = "How much work starting this commits to") int stepCount) {}
}
