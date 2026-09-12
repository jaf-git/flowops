package com.flowops.discovery.api.dto;

import com.flowops.discovery.application.proposetype.ProposeTypeUseCase.DiscoveredType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "DiscoveredType", description = "A shape the work of this business keeps taking.")
public record TypeRow(
        @Schema(description = "The type's identity, for naming or dismissing it.") UUID typeId,
        @Schema(description = "The owner's word for it. Absent until they give one — nothing here names itself.")
                String name,
        @Schema(
                        description =
                                """
                        CANDIDATE, PROPOSED, NAMED or PROVISIONAL. PROVISIONAL is a label rather than an
                        event: a type at its evidence floor oscillates week to week, so neither the
                        demotion nor the recovery is announced anywhere.
                        """)
                String status,
        @Schema(
                        description =
                                """
                        Counter C2 — completed THREADS of this shape. Never nodes, never cycles, never
                        messages, and it does not decay: a count answers how often, and a weight answers
                        how current.
                        """)
                int occurrenceCount,
        @Schema(description = "The role that asks. Absent where the threads were keyed on their performer alone.")
                String fromRoleName,
        @Schema(description = "The role that does the work.") String toRoleName,
        @Schema(description = "What a thread of this shape ends by producing.") String terminalOutputType) {
    public static TypeRow of(DiscoveredType type) {
        return new TypeRow(
                type.typeId(),
                type.name(),
                type.status().name(),
                type.occurrenceCount(),
                type.fromRoleName(),
                type.toRoleName(),
                type.terminalOutputType() == null
                        ? null
                        : type.terminalOutputType().name());
    }
}
