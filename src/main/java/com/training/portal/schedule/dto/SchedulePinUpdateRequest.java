package com.training.portal.schedule.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SchedulePinUpdateRequest(
        @NotNull Boolean pinned,
        @NotNull @PositiveOrZero Integer permissionUpdateCount) {
}
