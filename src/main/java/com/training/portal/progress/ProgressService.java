package com.training.portal.progress;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.training.portal.issue.IssueStatusRepository;
import com.training.portal.issue.IssueSubmissionComment;
import com.training.portal.issue.IssueSubmissionCommentId;
import com.training.portal.issue.IssueSubmissionCommentRepository;
import com.training.portal.issue.IssueSubmissionStatus;
import com.training.portal.issue.IssueSubmissionStatusId;
import com.training.portal.issue.IssueSubmissionStatusRepository;
import com.training.portal.member.KenshuMember;
import com.training.portal.member.KenshuMemberRepository;
import com.training.portal.progress.dto.ProgressCommentResponse;
import com.training.portal.progress.dto.ProgressQuery;
import com.training.portal.progress.dto.ProgressResponse;
import com.training.portal.progress.dto.ProgressSubmissionUpdateRequest;
import com.training.portal.progress.dto.ProgressSubmissionUpdateResponse;
import com.training.portal.progress.exception.ProgressAccessDeniedException;
import com.training.portal.progress.exception.ProgressInvalidInputException;
import com.training.portal.progress.exception.ProgressNotFoundException;
import com.training.portal.progress.exception.ProgressUpdateConflictException;
import com.training.portal.schedule.ScheduleBunruiRepository;
import com.training.portal.schedule.ScheduleCanEdit;
import com.training.portal.schedule.ScheduleCanEditRepository;
import com.training.portal.schedule.ScheduleDetail;
import com.training.portal.schedule.ScheduleDetailId;
import com.training.portal.schedule.ScheduleDetailRepository;

@Service
public class ProgressService {
    private static final short TRAINEE = 1;
    private static final short INSTRUCTOR = 2;
    private static final short UNSUBMITTED = 1;

    private final KenshuMemberRepository members;
    private final ScheduleCanEditRepository permissions;
    private final ScheduleBunruiRepository categories;
    private final ScheduleDetailRepository details;
    private final IssueStatusRepository statuses;
    private final IssueSubmissionStatusRepository submissions;
    private final IssueSubmissionCommentRepository comments;
    private final Clock businessClock;

    public ProgressService(KenshuMemberRepository members,
            ScheduleCanEditRepository permissions,
            ScheduleBunruiRepository categories,
            ScheduleDetailRepository details,
            IssueStatusRepository statuses,
            IssueSubmissionStatusRepository submissions,
            IssueSubmissionCommentRepository comments,
            Clock businessClock) {
        this.members = members;
        this.permissions = permissions;
        this.categories = categories;
        this.details = details;
        this.statuses = statuses;
        this.submissions = submissions;
        this.comments = comments;
        this.businessClock = businessClock;
    }

    @Transactional(readOnly = true)
    public ProgressResponse getProgress(String viewerId, ProgressQuery query) {
        KenshuMember viewer = requireViewer(viewerId);
        validateQuery(query);
        LocalDate today = LocalDate.now(businessClock);
        List<ScheduleCanEdit> accessible = permissions.findAccessibleSchedules(viewerId, today);
        Map<String, ScheduleCanEdit> permissionBySchedule = accessible.stream().collect(Collectors.toMap(
                permission -> permission.getId().getScheduleId(), Function.identity(), (a, b) -> a, LinkedHashMap::new));

        if (query.scheduleId() != null && !permissionBySchedule.containsKey(query.scheduleId())) {
            throw new ProgressAccessDeniedException();
        }
        if (accessible.isEmpty()) return emptyResponse(today, viewer);

        List<String> scheduleIds = query.scheduleId() == null
                ? new ArrayList<>(permissionBySchedule.keySet()) : List.of(query.scheduleId());
        var categoryRows = categories.findForSchedules(new ArrayList<>(permissionBySchedule.keySet()));
        var editablePermissions = permissions.findEditablePermissions(new ArrayList<>(permissionBySchedule.keySet()));
        if (query.categoryNo() != null) {
            if (query.scheduleId() == null) throw new ProgressInvalidInputException();
            boolean belongs = categoryRows.stream().anyMatch(row ->
                    row.getId().getScheduleId().equals(query.scheduleId())
                            && row.getId().getBnruiNo().equals(query.categoryNo()));
            if (!belongs) throw new ProgressInvalidInputException();
        }

        var statusRows = statuses.findAll().stream()
                .sorted(java.util.Comparator.comparing(row -> row.getTaskStatusKbn())).toList();
        Map<Short, String> statusNames = statusRows.stream().collect(Collectors.toMap(
                row -> row.getTaskStatusKbn(), row -> row.getTaskStatusName()));
        var allMembers = members.findAllByOrderBySyainIdAsc();
        Map<String, KenshuMember> memberMap = allMembers.stream().collect(Collectors.toMap(
                KenshuMember::getSyainId, Function.identity()));
        var taskRows = details.findProgressTasks(scheduleIds);
        if (query.scheduleDateNo() != null) {
            if (query.scheduleId() == null) throw new ProgressInvalidInputException();
            boolean exists = taskRows.stream().anyMatch(row ->
                    row.getId().getScheduleDateNo().equals(query.scheduleDateNo()));
            if (!exists) throw new ProgressNotFoundException();
        }

        var traineePermissions = permissions.findTraineePermissions(scheduleIds);
        Map<String, List<KenshuMember>> traineesBySchedule = traineePermissions.stream()
                .collect(Collectors.groupingBy(row -> row.getId().getScheduleId(), LinkedHashMap::new,
                        Collectors.mapping(ScheduleCanEdit::getMember, Collectors.toList())));
        validateRequestedTrainee(viewer, query, traineesBySchedule);

        List<IssueSubmissionStatus> submissionRows = viewer.getSyainKbn() == TRAINEE
                ? submissions.findForTrainee(scheduleIds, viewerId)
                : submissions.findForSchedules(scheduleIds);
        Map<SubmissionKey, IssueSubmissionStatus> submissionMap = submissionRows.stream().collect(Collectors.toMap(
                row -> key(row.getId()), Function.identity()));
        Map<SubmissionKey, List<IssueSubmissionComment>> commentsBySubmission = comments.findForSchedules(scheduleIds)
                .stream().collect(Collectors.groupingBy(row -> key(row.getId()), LinkedHashMap::new, Collectors.toList()));

        var items = new ArrayList<ProgressResponse.ProgressItem>();
        for (ScheduleDetail detail : taskRows) {
            String scheduleId = detail.getId().getScheduleId();
            ScheduleCanEdit viewerPermission = permissionBySchedule.get(scheduleId);
            List<KenshuMember> targetTrainees = viewer.getSyainKbn() == TRAINEE
                    ? traineesBySchedule.getOrDefault(scheduleId, List.of()).stream()
                            .filter(row -> row.getSyainId().equals(viewerId)).toList()
                    : traineesBySchedule.getOrDefault(scheduleId, List.of());
            for (KenshuMember trainee : targetTrainees) {
                SubmissionKey itemKey = new SubmissionKey(scheduleId,
                        detail.getId().getScheduleDateNo(), trainee.getSyainId());
                IssueSubmissionStatus submission = submissionMap.get(itemKey);
                short statusCode = submission == null ? UNSUBMITTED : submission.getTaskStatusKbn();
                String reviewerId = submission == null ? null : submission.getInstructorEmployeeId();
                if (!matches(query, detail, trainee, reviewerId, statusCode)) continue;

                var commentResponses = commentsBySubmission.getOrDefault(itemKey, List.of()).stream()
                        .map(comment -> toComment(comment, memberMap)).toList();
                var schedule = viewerPermission.getSchedule();
                items.add(new ProgressResponse.ProgressItem(
                        scheduleId, schedule.getScheduleName(), detail.getId().getScheduleDateNo(),
                        detail.getScheduleDate(), detail.getBnruiNo(), detail.getBunrui().getBnruiName(),
                        detail.getScheduleContent(), detail.getKoshiSyainId(), detail.getInstructor().getName(),
                        trainee.getSyainId(), trainee.getName(), reviewerId, memberName(memberMap, reviewerId),
                        statusCode, statusNames.getOrDefault(statusCode, "未提出"),
                        submission == null ? null : submission.getCompletedDate(),
                        viewerPermission.getCanEdit() == 1,
                        viewer.getSyainKbn() == TRAINEE || viewerPermission.getCanEdit() == 1,
                        submission == null ? null : submission.getUpdateCount(), commentResponses));
            }
        }

        return new ProgressResponse(today, roleName(viewer),
                scheduleOptions(accessible, categoryRows, editablePermissions),
                statusRows.stream().map(row -> new ProgressResponse.StatusOption(
                        row.getTaskStatusKbn(), row.getTaskStatusName())).toList(),
                items);
    }

    @Transactional
    public ProgressSubmissionUpdateResponse updateSubmission(String viewerId, String scheduleId,
            Integer scheduleDateNo, String traineeId, ProgressSubmissionUpdateRequest request) {
        requireItemAccess(viewerId, scheduleId, scheduleDateNo, traineeId, true);
        if (!statuses.existsById(request.statusCode())) throw new ProgressInvalidInputException();
        KenshuMember reviewer = null;
        if (request.reviewerEmployeeId() != null && !request.reviewerEmployeeId().isBlank()) {
            var reviewerPermission = permissions.findAccessibleSchedule(scheduleId, request.reviewerEmployeeId())
                    .orElseThrow(ProgressInvalidInputException::new);
            if (reviewerPermission.getCanEdit() != 1) throw new ProgressInvalidInputException();
            reviewer = members.findById(request.reviewerEmployeeId()).orElseThrow(ProgressInvalidInputException::new);
        }
        String reviewerId = reviewer == null ? null : reviewer.getSyainId();
        LocalDate completed = request.statusCode() == 6 || request.statusCode() == 7
                ? LocalDate.now(businessClock) : null;
        IssueSubmissionStatusId id = new IssueSubmissionStatusId(scheduleId, scheduleDateNo, traineeId);
        IssueSubmissionStatus submission = submissions.findForUpdate(id).orElse(null);
        if (submission == null) {
            if (request.updateCount() != null) throw new ProgressUpdateConflictException();
            submission = new IssueSubmissionStatus(id, reviewerId, request.statusCode(), completed, viewerId);
        } else {
            if (request.updateCount() == null || !request.updateCount().equals(submission.getUpdateCount())) {
                throw new ProgressUpdateConflictException();
            }
            submission.update(reviewerId, request.statusCode(), completed, viewerId);
        }
        try {
            submission = submissions.saveAndFlush(submission);
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
            throw new ProgressUpdateConflictException();
        }
        return new ProgressSubmissionUpdateResponse(reviewerId, reviewer == null ? null : reviewer.getName(),
                request.statusCode(), statuses.findById(request.statusCode()).orElseThrow().getTaskStatusName(),
                completed, submission.getUpdateCount());
    }

    @Transactional
    public ProgressCommentResponse addComment(String viewerId, String scheduleId,
            Integer scheduleDateNo, String traineeId, String content) {
        AccessContext access = requireItemAccess(viewerId, scheduleId, scheduleDateNo, traineeId, false);
        String normalized = normalizeComment(content);
        IssueSubmissionStatusId submissionId = new IssueSubmissionStatusId(scheduleId, scheduleDateNo, traineeId);
        if (submissions.findForUpdate(submissionId).isEmpty()) {
            try {
                submissions.saveAndFlush(new IssueSubmissionStatus(
                        submissionId, null, UNSUBMITTED, null, viewerId));
            } catch (DataIntegrityViolationException ex) {
                throw new ProgressUpdateConflictException();
            }
        }
        var existing = comments.lockSubmissionComments(scheduleId, scheduleDateNo, traineeId);
        long nextNo = existing.stream().mapToLong(row -> row.getId().getCommentNo()).max().orElse(0) + 1;
        var comment = comments.saveAndFlush(new IssueSubmissionComment(
                new IssueSubmissionCommentId(scheduleId, scheduleDateNo, traineeId, nextNo),
                viewerId, normalized, viewerId));
        return toCommentResponse(comment, access.viewer().getName());
    }

    @Transactional
    public ProgressCommentResponse updateComment(String viewerId, String scheduleId,
            Integer scheduleDateNo, String traineeId, Long commentNo, String content, Integer updateCount) {
        AccessContext access = requireItemAccess(viewerId, scheduleId, scheduleDateNo, traineeId, false);
        String normalized = normalizeComment(content);
        var rows = comments.lockSubmissionComments(scheduleId, scheduleDateNo, traineeId);
        var comment = rows.stream().filter(row -> row.getId().getCommentNo().equals(commentNo))
                .findFirst().orElseThrow(ProgressNotFoundException::new);
        if (!viewerId.equals(comment.getCommentEmployeeId())) throw new ProgressAccessDeniedException();
        if (updateCount == null || !updateCount.equals(comment.getUpdateCount())) {
            throw new ProgressUpdateConflictException();
        }
        comment.update(normalized, viewerId);
        try {
            comment = comments.saveAndFlush(comment);
        } catch (OptimisticLockingFailureException ex) {
            throw new ProgressUpdateConflictException();
        }
        String authorName = memberNameById(comment.getCommentEmployeeId());
        return toCommentResponse(comment, authorName);
    }

    @Transactional
    public void deleteComment(String viewerId, String scheduleId, Integer scheduleDateNo,
            String traineeId, Long commentNo, Integer updateCount) {
        requireItemViewAccess(viewerId, scheduleId, scheduleDateNo, traineeId);
        var rows = comments.lockSubmissionComments(scheduleId, scheduleDateNo, traineeId);
        var comment = rows.stream().filter(row -> row.getId().getCommentNo().equals(commentNo))
                .findFirst().orElseThrow(ProgressNotFoundException::new);
        if (!viewerId.equals(comment.getCommentEmployeeId())) {
            throw new ProgressAccessDeniedException();
        }
        if (updateCount == null || !updateCount.equals(comment.getUpdateCount())) {
            throw new ProgressUpdateConflictException();
        }
        comments.delete(comment);
        comments.flush();
    }

    private AccessContext requireItemAccess(String viewerId, String scheduleId, Integer scheduleDateNo,
            String traineeId, boolean editRequired) {
        details.findForTaskUpdate(new ScheduleDetailId(scheduleId, scheduleDateNo))
                .orElseThrow(ProgressNotFoundException::new);
        AccessContext access = requireItemViewAccess(viewerId, scheduleId, scheduleDateNo, traineeId);
        if (editRequired && access.permission().getCanEdit() != 1) throw new ProgressAccessDeniedException();
        if (!editRequired && access.viewer().getSyainKbn() == INSTRUCTOR
                && access.permission().getCanEdit() != 1) {
            throw new ProgressAccessDeniedException();
        }
        return access;
    }

    private AccessContext requireItemViewAccess(String viewerId, String scheduleId, Integer scheduleDateNo,
            String traineeId) {
        KenshuMember viewer = requireViewer(viewerId);
        ScheduleCanEdit permission = permissions.findAccessibleSchedule(scheduleId, viewerId)
                .orElseThrow(ProgressAccessDeniedException::new);
        ScheduleDetail detail = details.findById(new ScheduleDetailId(scheduleId, scheduleDateNo))
                .orElseThrow(ProgressNotFoundException::new);
        if (detail.getTaskFlg() != 1) throw new ProgressNotFoundException();
        KenshuMember trainee = members.findById(traineeId).orElseThrow(ProgressNotFoundException::new);
        if (trainee.getSyainKbn() != TRAINEE
                || permissions.findAccessibleSchedule(scheduleId, traineeId).isEmpty()) {
            throw new ProgressAccessDeniedException();
        }
        if (viewer.getSyainKbn() == TRAINEE && !viewerId.equals(traineeId)) {
            throw new ProgressAccessDeniedException();
        }
        return new AccessContext(viewer, permission, detail, trainee);
    }

    private KenshuMember requireViewer(String viewerId) {
        KenshuMember viewer = members.findById(viewerId).orElseThrow(ProgressAccessDeniedException::new);
        if (viewer.getSyainKbn() != TRAINEE && viewer.getSyainKbn() != INSTRUCTOR) {
            throw new ProgressAccessDeniedException();
        }
        return viewer;
    }

    private void validateQuery(ProgressQuery query) {
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new ProgressInvalidInputException();
        }
        if (query.statusCode() != null && (query.statusCode() < 1 || query.statusCode() > 7)) {
            throw new ProgressInvalidInputException();
        }
    }

    private void validateRequestedTrainee(KenshuMember viewer, ProgressQuery query,
            Map<String, List<KenshuMember>> traineesBySchedule) {
        if (query.traineeEmployeeId() == null) return;
        if (viewer.getSyainKbn() == TRAINEE && !viewer.getSyainId().equals(query.traineeEmployeeId())) {
            throw new ProgressAccessDeniedException();
        }
        if (query.scheduleId() == null) throw new ProgressInvalidInputException();
        boolean target = traineesBySchedule.getOrDefault(query.scheduleId(), List.of()).stream()
                .anyMatch(row -> row.getSyainId().equals(query.traineeEmployeeId()));
        if (!target) throw new ProgressAccessDeniedException();
    }

    private boolean matches(ProgressQuery query, ScheduleDetail detail, KenshuMember trainee,
            String reviewerId, short statusCode) {
        if (query.categoryNo() != null && !detail.getBnruiNo().equals(query.categoryNo())) return false;
        if (query.scheduleDateNo() != null
                && !detail.getId().getScheduleDateNo().equals(query.scheduleDateNo())) return false;
        if (query.traineeEmployeeId() != null
                && !trainee.getSyainId().equals(query.traineeEmployeeId())) return false;
        if (query.submitter() != null && !query.submitter().isBlank()) {
            String term = query.submitter().strip().toLowerCase(java.util.Locale.ROOT);
            if (!trainee.getSyainId().toLowerCase(java.util.Locale.ROOT).contains(term)
                    && !trainee.getName().toLowerCase(java.util.Locale.ROOT).contains(term)) return false;
        }
        if (query.from() != null && detail.getScheduleDate().isBefore(query.from())) return false;
        if (query.to() != null && detail.getScheduleDate().isAfter(query.to())) return false;
        if (query.reviewerEmployeeId() != null
                && !query.reviewerEmployeeId().equals(reviewerId)) return false;
        return query.statusCode() == null || query.statusCode() == statusCode;
    }

    private List<ProgressResponse.ScheduleOption> scheduleOptions(List<ScheduleCanEdit> accessible,
            List<com.training.portal.schedule.ScheduleBunrui> categoryRows,
            List<ScheduleCanEdit> editablePermissions) {
        Map<String, List<ProgressResponse.CategoryOption>> grouped = categoryRows.stream()
                .collect(Collectors.groupingBy(row -> row.getId().getScheduleId(), LinkedHashMap::new,
                        Collectors.mapping(row -> new ProgressResponse.CategoryOption(
                                row.getId().getBnruiNo(), row.getBnruiName()), Collectors.toList())));
        Map<String, List<ProgressResponse.MemberOption>> reviewersBySchedule = editablePermissions.stream()
                .collect(Collectors.groupingBy(row -> row.getId().getScheduleId(), LinkedHashMap::new,
                        Collectors.mapping(row -> new ProgressResponse.MemberOption(
                                row.getMember().getSyainId(), row.getMember().getName()), Collectors.toList())));
        return accessible.stream().map(permission -> new ProgressResponse.ScheduleOption(
                permission.getId().getScheduleId(), permission.getSchedule().getScheduleName(),
                permission.getCanEdit() == 1,
                grouped.getOrDefault(permission.getId().getScheduleId(), List.of()),
                reviewersBySchedule.getOrDefault(permission.getId().getScheduleId(), List.of()))).toList();
    }

    private ProgressResponse emptyResponse(LocalDate today, KenshuMember viewer) {
        var statusRows = statuses.findAll().stream()
                .sorted(java.util.Comparator.comparing(row -> row.getTaskStatusKbn())).toList();
        return new ProgressResponse(today, roleName(viewer), List.of(),
                statusRows.stream().map(row -> new ProgressResponse.StatusOption(
                        row.getTaskStatusKbn(), row.getTaskStatusName())).toList(), List.of());
    }

    private ProgressResponse.Comment toComment(IssueSubmissionComment comment,
            Map<String, KenshuMember> memberMap) {
        return new ProgressResponse.Comment(comment.getId().getCommentNo(), comment.getCommentEmployeeId(),
                memberName(memberMap, comment.getCommentEmployeeId()), comment.getContent(),
                comment.getUpdateDate(), comment.getUpdateCount());
    }

    private ProgressCommentResponse toCommentResponse(IssueSubmissionComment comment, String authorName) {
        return new ProgressCommentResponse(comment.getId().getCommentNo(), comment.getCommentEmployeeId(),
                authorName, comment.getContent(), comment.getUpdateDate(), comment.getUpdateCount());
    }

    private String normalizeComment(String content) {
        if (content == null || content.isBlank() || content.length() > 200) {
            throw new ProgressInvalidInputException();
        }
        return content.strip();
    }

    private String memberNameById(String memberId) {
        return memberId == null ? null : members.findById(memberId).map(KenshuMember::getName).orElse("—");
    }

    private String memberName(Map<String, KenshuMember> memberMap, String memberId) {
        if (memberId == null) return null;
        KenshuMember member = memberMap.get(memberId);
        return member == null ? "—" : member.getName();
    }

    private SubmissionKey key(IssueSubmissionStatusId id) {
        return new SubmissionKey(id.getScheduleId(), id.getScheduleDateNo(), id.getTraineeEmployeeId());
    }

    private SubmissionKey key(IssueSubmissionCommentId id) {
        return new SubmissionKey(id.getScheduleId(), id.getScheduleDateNo(), id.getTraineeEmployeeId());
    }

    private String roleName(KenshuMember viewer) {
        return viewer.getSyainKbn() == INSTRUCTOR ? "INSTRUCTOR" : "TRAINEE";
    }

    private record SubmissionKey(String scheduleId, Integer scheduleDateNo, String traineeId) {
    }

    private record AccessContext(KenshuMember viewer, ScheduleCanEdit permission,
            ScheduleDetail detail, KenshuMember trainee) {
    }
}
