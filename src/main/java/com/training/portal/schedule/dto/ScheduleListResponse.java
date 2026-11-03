package com.training.portal.schedule.dto;

import java.time.LocalDate;

import com.training.portal.schedule.ScheduleCanEdit;

public record ScheduleListResponse(
        String scheduleId,
        String scheduleName,
        LocalDate startDate,
        LocalDate endDate,
        boolean canEdit,
        boolean pinned,
        Integer scheduleUpdateCount,
        Integer permissionUpdateCount) {

    public static ScheduleListResponse from(ScheduleCanEdit permission) {
        var schedule = permission.getSchedule();
        return new ScheduleListResponse(
                schedule.getScheduleId(),
                schedule.getScheduleName(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                permission.getCanEdit() == 1,
                permission.getPinFlg() == 1,
                schedule.getUpdateCount(),
                permission.getUpdateCount());
    }
}
