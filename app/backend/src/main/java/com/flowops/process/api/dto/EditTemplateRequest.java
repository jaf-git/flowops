package com.flowops.process.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record EditTemplateRequest(@Schema(example = "Varianta scurtă") String overview, List<StepRequest> steps) {}
