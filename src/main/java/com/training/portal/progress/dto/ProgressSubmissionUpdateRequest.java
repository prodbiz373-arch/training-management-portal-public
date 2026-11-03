package com.training.portal.progress.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProgressSubmissionUpdateRequest(
        @Size(max = 10) String reviewerEmployeeId,
        @NotNull @Min(1) @Max(7) Short statusCode,
        @Min(0) Integer updateCount) {
}
