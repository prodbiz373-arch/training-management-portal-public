package com.training.portal.home.dto;

import java.time.LocalDate;
import java.util.List;

public record HomeDashboardResponse(
        LocalDate businessDate,
        String viewerRole,
        TaskSummary taskSummary,
        List<ScheduleSummary> schedules) {

    public record TaskSummary(int unsubmitted, int correctionRequired, int inReview, int completed) {
    }

    public record ScheduleSummary(
            String scheduleId,
            String scheduleName,
            LocalDate startDate,
            LocalDate endDate,
            boolean canEdit,
            boolean pinned,
            List<ScheduleRow> details) {
    }

    public record ScheduleRow(
            Integer scheduleDateNo,
            LocalDate scheduleDate,
            Short categoryNo,
            String categoryName,
            String content,
            String instructorName,
            boolean taskFlg,
            Short taskStatusCode,
            String taskStatusName,
            Integer completedCount,
            Integer targetTraineeCount) {
    }
}
