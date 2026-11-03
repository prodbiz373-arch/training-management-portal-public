package com.training.portal.progress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ProgressCommentRequest(
        @NotBlank @Size(max = 200) String content,
        @Min(0) Integer updateCount) {
}
