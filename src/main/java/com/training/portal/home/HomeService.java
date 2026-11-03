package com.training.portal.home;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.training.portal.home.dto.HomeDashboardResponse;
import com.training.portal.issue.IssueStatusRepository;
import com.training.portal.issue.IssueSubmissionStatus;
import com.training.portal.issue.IssueSubmissionStatusRepository;
import com.training.portal.member.KenshuMemberRepository;
import com.training.portal.schedule.ScheduleCanEditRepository;
import com.training.portal.schedule.ScheduleDetail;
import com.training.portal.schedule.ScheduleDetailRepository;
import com.training.portal.schedule.exception.ScheduleAccessDeniedException;

@Service
@Transactional(readOnly = true)
public class HomeService {
    private static final short TRAINEE = 1;
    private static final short INSTRUCTOR = 2;
    private static final short UNSUBMITTED = 1;

    private final KenshuMemberRepository members;
    private final ScheduleCanEditRepository permissions;
    private final ScheduleDetailRepository details;
    private final IssueSubmissionStatusRepository submissions;
    private final IssueStatusRepository statuses;
    private final Clock businessClock;

    public HomeService(
            KenshuMemberRepository members,
            ScheduleCanEditRepository permissions,
            ScheduleDetailRepository details,
            IssueSubmissionStatusRepository submissions,
            IssueStatusRepository statuses,
            Clock businessClock) {
        this.members = members;
        this.permissions = permissions;
        this.details = details;
        this.submissions = submissions;
        this.statuses = statuses;
        this.businessClock = businessClock;
    }

    public HomeDashboardResponse getDashboard(String syainId) {
        var viewer = members.findById(syainId).orElseThrow(ScheduleAccessDeniedException::new);
        short viewerType = viewer.getSyainKbn();
        if (viewerType != TRAINEE && viewerType != INSTRUCTOR) throw new ScheduleAccessDeniedException();

        LocalDate today = LocalDate.now(businessClock);
        var accessible = permissions.findAccessibleSchedules(syainId, today);
        if (accessible.isEmpty()) {
            return new HomeDashboardResponse(today, roleName(viewerType),
                    new HomeDashboardResponse.TaskSummary(0, 0, 0, 0), List.of());
        }

        var scheduleIds = accessible.stream().map(row -> row.getId().getScheduleId()).toList();
        var scheduleDetails = details.findHomeDetails(scheduleIds, today);
        var traineePermissions = permissions.findTraineePermissions(scheduleIds);
        var issueSubmissions = viewerType == TRAINEE
                ? submissions.findForTrainee(scheduleIds, syainId)
                : submissions.findForSchedules(scheduleIds);
        var statusNames = statuses.findAll().stream().collect(Collectors.toMap(
                row -> row.getTaskStatusKbn(), row -> row.getTaskStatusName()));

        var detailsBySchedule = scheduleDetails.stream()
                .collect(Collectors.groupingBy(row -> row.getId().getScheduleId(),
                        java.util.LinkedHashMap::new, Collectors.toList()));
        var traineesBySchedule = traineePermissions.stream()
                .collect(Collectors.groupingBy(row -> row.getId().getScheduleId(),
                        Collectors.mapping(row -> row.getId().getSyainId(), Collectors.toList())));
        var submissionMap = issueSubmissions.stream().collect(Collectors.toMap(
                row -> new SubmissionKey(row.getId().getScheduleId(), row.getId().getScheduleDateNo(),
                        row.getId().getTraineeEmployeeId()),
                Function.identity()));

        var summaryCounter = new int[4];
        var scheduleResponses = new ArrayList<HomeDashboardResponse.ScheduleSummary>();
        for (var permission : accessible) {
            String scheduleId = permission.getId().getScheduleId();
            var targetTrainees = traineesBySchedule.getOrDefault(scheduleId, List.of());
            var rows = new ArrayList<HomeDashboardResponse.ScheduleRow>();
            for (var detail : detailsBySchedule.getOrDefault(scheduleId, List.of())) {
                var row = toRow(detail, syainId, viewerType, targetTrainees, submissionMap, statusNames);
                rows.add(row);
                if (detail.getTaskFlg() == 1 && !detail.getScheduleDate().isAfter(today)) {
                    if (viewerType == TRAINEE) {
                        count(summaryCounter, statusFor(submissionMap, scheduleId,
                                detail.getId().getScheduleDateNo(), syainId));
                    } else {
                        for (var traineeId : targetTrainees) {
                            count(summaryCounter, statusFor(submissionMap, scheduleId,
                                    detail.getId().getScheduleDateNo(), traineeId));
                        }
                    }
                }
            }
            var schedule = permission.getSchedule();
            scheduleResponses.add(new HomeDashboardResponse.ScheduleSummary(
                    scheduleId,
                    schedule.getScheduleName(),
                    schedule.getStartDate(),
                    schedule.getEndDate(),
                    permission.getCanEdit() == 1,
                    permission.getPinFlg() == 1,
                    rows));
        }

        return new HomeDashboardResponse(
                today,
                roleName(viewerType),
                new HomeDashboardResponse.TaskSummary(
                        summaryCounter[0], summaryCounter[1], summaryCounter[2], summaryCounter[3]),
                scheduleResponses);
    }

    private HomeDashboardResponse.ScheduleRow toRow(
            ScheduleDetail detail,
            String syainId,
            short viewerType,
            List<String> targetTrainees,
            Map<SubmissionKey, IssueSubmissionStatus> submissionMap,
            Map<Short, String> statusNames) {
        if (detail.getTaskFlg() != 1) {
            return row(detail, null, null, null, null);
        }
        if (viewerType == TRAINEE) {
            short status = statusFor(submissionMap, detail.getId().getScheduleId(),
                    detail.getId().getScheduleDateNo(), syainId);
            return row(detail, status, statusNames.getOrDefault(status, status == UNSUBMITTED ? "未提出" : null),
                    null, null);
        }
        int complete = (int) targetTrainees.stream()
                .map(trainee -> statusFor(submissionMap, detail.getId().getScheduleId(),
                        detail.getId().getScheduleDateNo(), trainee))
                .filter(status -> status == 6 || status == 7)
                .count();
        return row(detail, null, null, complete, targetTrainees.size());
    }

    private HomeDashboardResponse.ScheduleRow row(
            ScheduleDetail detail,
            Short taskStatusCode,
            String taskStatusName,
            Integer completedCount,
            Integer targetTraineeCount) {
        return new HomeDashboardResponse.ScheduleRow(
                detail.getId().getScheduleDateNo(),
                detail.getScheduleDate(),
                detail.getBnruiNo(),
                detail.getBunrui().getBnruiName(),
                detail.getScheduleContent(),
                detail.getInstructor().getName(),
                detail.getTaskFlg() == 1,
                taskStatusCode,
                taskStatusName,
                completedCount,
                targetTraineeCount);
    }

    private short statusFor(
            Map<SubmissionKey, IssueSubmissionStatus> submissionMap,
            String scheduleId,
            Integer scheduleDateNo,
            String traineeId) {
        var submission = submissionMap.get(new SubmissionKey(scheduleId, scheduleDateNo, traineeId));
        return submission == null ? UNSUBMITTED : submission.getTaskStatusKbn();
    }

    private void count(int[] counters, short status) {
        switch (status) {
            case 1 -> counters[0]++;
            case 2 -> counters[1]++;
            case 3, 4, 5 -> counters[2]++;
            case 6, 7 -> counters[3]++;
            default -> {
                // DB制約とマスタ外の値は集計しない。
            }
        }
    }

    private String roleName(short viewerType) {
        return viewerType == INSTRUCTOR ? "INSTRUCTOR" : "TRAINEE";
    }

    private record SubmissionKey(String scheduleId, Integer scheduleDateNo, String traineeId) {
    }
}
