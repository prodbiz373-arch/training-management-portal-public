package com.training.portal.schedule.dto;

public record SchedulePinUpdateResponse(
        String scheduleId,
        boolean pinned,
        Integer permissionUpdateCount) {
}
