package com.training.portal.progress.dto;

import java.time.LocalDateTime;

public record ProgressCommentResponse(
        Long commentNo,
        String authorEmployeeId,
        String authorName,
        String content,
        LocalDateTime updatedAt,
        Integer updateCount) {
}
