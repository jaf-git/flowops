package com.flowops.process.api.dto;

import com.flowops.process.domain.model.ProcessMetadata;

public record ProcessMetadataResponse(String triggerNote, String endCondition, String ownerRole) {
    public static ProcessMetadataResponse of(ProcessMetadata metadata) {
        return new ProcessMetadataResponse(metadata.triggerNote(), metadata.endCondition(), metadata.ownerRole());
    }
}
