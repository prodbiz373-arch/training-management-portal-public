package com.training.portal.schedule.exception;

public class ScheduleHasIssueSubmissionsException extends RuntimeException {
    public ScheduleHasIssueSubmissionsException() {
        super("課題提出状況が登録されているため、このスケジュールは削除できません。");
    }
}
