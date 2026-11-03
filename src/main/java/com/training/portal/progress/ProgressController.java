package com.training.portal.progress;

import java.security.Principal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.training.portal.progress.dto.ProgressCommentRequest;
import com.training.portal.progress.dto.ProgressCommentResponse;
import com.training.portal.progress.dto.ProgressQuery;
import com.training.portal.progress.dto.ProgressResponse;
import com.training.portal.progress.dto.ProgressSubmissionUpdateRequest;
import com.training.portal.progress.dto.ProgressSubmissionUpdateResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {
    private final ProgressService service;

    public ProgressController(ProgressService service) {
        this.service = service;
    }

    @GetMapping
    public ProgressResponse getProgress(
            @RequestParam(required = false) String scheduleId,
            @RequestParam(required = false) Short categoryNo,
            @RequestParam(required = false) String submitter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String reviewerEmployeeId,
            @RequestParam(required = false) Short statusCode,
            @RequestParam(required = false) Integer scheduleDateNo,
            @RequestParam(required = false) String traineeEmployeeId,
            Principal principal) {
        return service.getProgress(principal.getName(), new ProgressQuery(scheduleId, categoryNo,
                submitter, from, to, reviewerEmployeeId, statusCode, scheduleDateNo, traineeEmployeeId));
    }

    @PutMapping("/{scheduleId}/{scheduleDateNo}/trainees/{traineeId}")
    public ProgressSubmissionUpdateResponse updateSubmission(
            @PathVariable String scheduleId,
            @PathVariable Integer scheduleDateNo,
            @PathVariable String traineeId,
            @Valid @RequestBody ProgressSubmissionUpdateRequest request,
            Principal principal) {
        return service.updateSubmission(principal.getName(), scheduleId, scheduleDateNo, traineeId, request);
    }

    @PostMapping("/{scheduleId}/{scheduleDateNo}/trainees/{traineeId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgressCommentResponse addComment(
            @PathVariable String scheduleId,
            @PathVariable Integer scheduleDateNo,
            @PathVariable String traineeId,
            @Valid @RequestBody ProgressCommentRequest request,
            Principal principal) {
        return service.addComment(principal.getName(), scheduleId, scheduleDateNo, traineeId, request.content());
    }

    @PutMapping("/{scheduleId}/{scheduleDateNo}/trainees/{traineeId}/comments/{commentNo}")
    public ProgressCommentResponse updateComment(
            @PathVariable String scheduleId,
            @PathVariable Integer scheduleDateNo,
            @PathVariable String traineeId,
            @PathVariable Long commentNo,
            @Valid @RequestBody ProgressCommentRequest request,
            Principal principal) {
        return service.updateComment(principal.getName(), scheduleId, scheduleDateNo,
                traineeId, commentNo, request.content(), request.updateCount());
    }

    @DeleteMapping("/{scheduleId}/{scheduleDateNo}/trainees/{traineeId}/comments/{commentNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            @PathVariable String scheduleId,
            @PathVariable Integer scheduleDateNo,
            @PathVariable String traineeId,
            @PathVariable Long commentNo,
            @RequestParam Integer updateCount,
            Principal principal) {
        service.deleteComment(principal.getName(), scheduleId, scheduleDateNo,
                traineeId, commentNo, updateCount);
    }
}
