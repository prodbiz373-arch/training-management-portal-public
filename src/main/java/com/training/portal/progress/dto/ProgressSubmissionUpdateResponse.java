package com.training.portal.progress.dto;

import java.time.LocalDate;

public record ProgressSubmissionUpdateResponse(
        String reviewerEmployeeId,
        String reviewerName,
        Short statusCode,
        String statusName,
        LocalDate completedDate,
        Integer updateCount) {
}
