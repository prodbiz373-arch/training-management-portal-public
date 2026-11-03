package com.training.portal.schedule.dto;

import java.time.LocalDate;
import java.util.List;

public record ScheduleNonWorkingDatesResponse(List<LocalDate> dates) {
}
