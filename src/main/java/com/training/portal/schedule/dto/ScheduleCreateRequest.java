package com.training.portal.schedule.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ScheduleCreateRequest(
        @NotBlank @Size(max = 10) @Pattern(regexp = "[A-Za-z0-9_-]+") String scheduleId,
        @NotBlank @Size(max = 40) String scheduleName,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull Boolean excludeHolidays,
        @NotEmpty @Valid List<Permission> permissions,
        @NotEmpty @Size(max = 99) @Valid List<Category> categories,
        @NotEmpty @Valid List<Detail> details) {

    public record Permission(
            @NotBlank @Size(max = 10) String employeeId,
            @NotNull Boolean canEdit) {
    }

    public record Category(
            @NotNull @Min(1) @Max(99) Short categoryNo,
            @NotBlank @Size(max = 30) String categoryName) {
    }

    public record Detail(
            @NotNull @Min(1) Integer scheduleDateNo,
            @NotNull LocalDate scheduleDate,
            @NotNull @Min(1) @Max(99) Short categoryNo,
            @NotBlank @Size(max = 10) String instructorEmployeeId,
            @Size(max = 50) String content,
            @NotNull Boolean taskFlg) {
    }

    @Override
    public String toString() {
        return "ScheduleCreateRequest[内容非表示]";
    }
}
