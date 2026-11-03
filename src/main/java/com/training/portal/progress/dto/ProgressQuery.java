package com.training.portal.progress.dto;

import java.time.LocalDate;

public record ProgressQuery(
        String scheduleId,
        Short categoryNo,
        String submitter,
        LocalDate from,
        LocalDate to,
        String reviewerEmployeeId,
        Short statusCode,
        Integer scheduleDateNo,
        String traineeEmployeeId) {
}
