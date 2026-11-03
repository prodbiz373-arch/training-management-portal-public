package com.training.portal.schedule.exception;

public class SchedulePinLimitExceededException extends RuntimeException {
    public SchedulePinLimitExceededException() {
        super("ピン留めできるスケジュールは2件までです。");
    }
}
