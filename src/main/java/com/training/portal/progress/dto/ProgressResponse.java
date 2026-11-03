package com.training.portal.progress.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ProgressResponse(
        LocalDate businessDate,
        String viewerRole,
        List<ScheduleOption> schedules,
        List<StatusOption> statuses,
        List<ProgressItem> items) {

    public record ScheduleOption(String scheduleId, String scheduleName, boolean canEdit,
            List<CategoryOption> categories, List<MemberOption> reviewers) {
    }

    public record CategoryOption(Short categoryNo, String categoryName) {
    }

    public record MemberOption(String employeeId, String name) {
    }

    public record StatusOption(Short statusCode, String statusName) {
    }

    public record ProgressItem(
            String scheduleId,
            String scheduleName,
            Integer scheduleDateNo,
            LocalDate scheduleDate,
            Short categoryNo,
            String categoryName,
            String content,
            String assignedInstructorEmployeeId,
            String assignedInstructorName,
            String traineeEmployeeId,
            String traineeName,
            String reviewerEmployeeId,
            String reviewerName,
            Short statusCode,
            String statusName,
            LocalDate completedDate,
            boolean canEdit,
            boolean canComment,
            Integer submissionUpdateCount,
            List<Comment> comments) {
    }

    public record Comment(
            Long commentNo,
            String authorEmployeeId,
            String authorName,
            String content,
            LocalDateTime updatedAt,
            Integer updateCount) {
    }
}
