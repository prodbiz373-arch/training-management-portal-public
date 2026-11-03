package com.training.portal.schedule.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ScheduleUpdateRequest(
        @NotBlank @Size(max = 40) String scheduleName,
        @NotNull @PositiveOrZero Integer scheduleUpdateCount,
        @NotNull @Valid List<Permission> permissions,
        @NotNull @Valid List<DeletedPermission> deletedPermissions,
        @NotNull @Size(max = 99) @Valid List<Category> categories,
        @NotNull @Valid List<DeletedCategory> deletedCategories,
        @NotNull @Valid List<Detail> details) {

    public record Permission(
            @NotBlank @Size(max = 10) String employeeId,
            @NotNull Boolean canEdit,
            @NotNull @PositiveOrZero Integer updateCount) {
    }

    public record DeletedPermission(
            @NotBlank @Size(max = 10) String employeeId,
            @NotNull @PositiveOrZero Integer updateCount) {
    }

    public record Category(
            @NotNull @Min(1) @Max(99) Short categoryNo,
            @NotBlank @Size(max = 30) String categoryName,
            @NotNull @PositiveOrZero Integer updateCount) {
    }

    public record DeletedCategory(
            @NotNull @Min(1) @Max(99) Short categoryNo,
            @NotNull @PositiveOrZero Integer updateCount) {
    }

    public record Detail(
            @NotNull @Min(1) Integer scheduleDateNo,
            @NotNull @Min(1) @Max(99) Short categoryNo,
            @NotBlank @Size(max = 10) String instructorEmployeeId,
            @Size(max = 50) String content,
            Boolean taskFlg,
            @NotNull @PositiveOrZero Integer updateCount) {
    }

    @Override
    public String toString() {
        return "ScheduleUpdateRequest[内容非表示]";
    }
}
