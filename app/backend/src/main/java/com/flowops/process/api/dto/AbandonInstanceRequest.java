package com.flowops.process.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AbandonInstanceRequest(
        @NotBlank
                @Size(max = 2000)
                @Schema(
                        description =
                                "Why the run is being stopped. Everybody who had a task in it will" + " read this.",
                        example = "Clientul a anulat comanda; nu mai continuăm producția.")
                String reason) {}
